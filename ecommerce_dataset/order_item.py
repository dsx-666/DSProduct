#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
订单明细数据清洗 + MySQL 建表 + 批量入库

输入：
    order_items.csv

原始字段：
    order_item_id
    order_id
    product_id
    user_id
    quantity
    item_price
    item_total

处理规则：
1. order_item_id：
   - 去掉首字母 I
   - 转换为整数
   - 作为 order_item 表主键
   例如：I00000001 -> 1

2. order_id / product_id / user_id（外键）：
   - 分别去掉首字母 O / P / U
   - 转换为整数
   - 必须在其对应父表主键中存在，否则整行剔除
       order_id   -> order.order_id
       product_id -> product.product_id
       user_id    -> user.user_id

3. quantity：
   - 清洗并转换为整数
   - 不能为空
   - 必须大于 0

4. item_price：
   - 清洗并转换为浮点数
   - 不能为空
   - 不能小于 0

5. item_total：
   - 清洗并转换为浮点数
   - 不能为空
   - 不能小于 0

6. 等式关系校验：
   - item_total 必须约等于 quantity * item_price
   - 误差容忍 0.01（浮点四舍五入误差）
   - 不满足等式的记录整行剔除

7. order_item_id 主键唯一，重复保留第一条

8. 使用事务批量插入：
   - 全部成功 -> COMMIT
   - 出现异常 -> ROLLBACK

数据库配置暂时留空。
"""

from pathlib import Path

import pandas as pd
import mysql.connector
from mysql.connector import Error


# ============================================================
# 1. 数据库配置
# ============================================================

DB_CONFIG = {
    "host": "127.0.0.1",          # 例如：127.0.0.1
    "port": 3306,
    "user": "root",               # 例如：root
    "password": "123589674xing",
    "database": "DSProduct",      # 例如：product
    "charset": "utf8mb4",
}

CSV_PATH = Path(__file__).with_name("order_items.csv")

BATCH_SIZE = 500

# item_total 与 quantity*item_price 的等式误差容忍
PRICE_TOLERANCE = 0.01


# ============================================================
# 2. 通用 ID 转换（去掉指定首字母 -> 整数）
# ============================================================

def convert_id(value, prefix):
    """
    去掉首字母前缀后转整数。
    例：convert_id("I00000001", "I") -> 1
        convert_id("O00000002", "O") -> 2
        convert_id("P001758",   "P") -> 1758
        convert_id("U009310",   "U") -> 9310

    空值 / 前缀不符 / 非数字 一律返回 None。
    """

    if pd.isna(value):
        return None

    value = str(value).strip()

    if not value.startswith(prefix):
        return None

    number_part = value[1:]

    if not number_part.isdigit():
        return None

    return int(number_part)


# ============================================================
# 3. 从父表读取主键集合（用于外键完整性校验）
# ============================================================

def load_parent_keys(table, key_column):
    """
    读取某张父表已有主键集合。
    父表不存在或查询失败时返回空集合（即视为全部外键非法）。
    """

    connection = None
    cursor = None

    try:

        connection = mysql.connector.connect(**DB_CONFIG)

        cursor = connection.cursor()

        cursor.execute(
            "SELECT `{key}` FROM `{tbl}`".format(
                key=key_column, tbl=table
            )
        )

        return {row[0] for row in cursor.fetchall()}

    except Error:
        return set()

    finally:

        if cursor is not None:
            cursor.close()

        if connection is not None and connection.is_connected():
            connection.close()


# ============================================================
# 4. 数据清洗
# ============================================================

def clean_data(df: pd.DataFrame) -> pd.DataFrame:

    required_columns = {
        "order_item_id",
        "order_id",
        "product_id",
        "user_id",
        "quantity",
        "item_price",
        "item_total",
    }

    missing_columns = required_columns - set(df.columns)

    if missing_columns:
        raise ValueError(
            f"CSV 缺少必要字段：{sorted(missing_columns)}"
        )

    original_count = len(df)

    # --------------------------------------------------------
    # order_item_id（主键）
    # --------------------------------------------------------

    df["order_item_id"] = df["order_item_id"].apply(
        lambda v: convert_id(v, "I")
    )

    invalid_item_id_count = int(df["order_item_id"].isna().sum())

    df = df[df["order_item_id"].notna()].copy()

    df["order_item_id"] = df["order_item_id"].astype("int64")

    # --------------------------------------------------------
    # 外键：order_id / product_id / user_id
    # --------------------------------------------------------

    df["order_id"] = df["order_id"].apply(
        lambda v: convert_id(v, "O")
    )
    df["product_id"] = df["product_id"].apply(
        lambda v: convert_id(v, "P")
    )
    df["user_id"] = df["user_id"].apply(
        lambda v: convert_id(v, "U")
    )

    invalid_fk_format_count = int(
        (
            df["order_id"].isna()
            | df["product_id"].isna()
            | df["user_id"].isna()
        ).sum()
    )

    df = df[
        df["order_id"].notna()
        & df["product_id"].notna()
        & df["user_id"].notna()
    ].copy()

    df["order_id"] = df["order_id"].astype("int64")
    df["product_id"] = df["product_id"].astype("int64")
    df["user_id"] = df["user_id"].astype("int64")

    # --------------------------------------------------------
    # quantity
    # --------------------------------------------------------

    df["quantity"] = pd.to_numeric(df["quantity"], errors="coerce")

    invalid_quantity_count = int(df["quantity"].isna().sum())

    df = df[df["quantity"].notna()].copy()

    # 必须是整数（出现小数按非法处理）
    non_integer_quantity_count = int(
        (df["quantity"] % 1 != 0).sum()
    )

    df = df[df["quantity"] % 1 == 0].copy()

    df["quantity"] = df["quantity"].astype("int64")

    non_positive_quantity_count = int(
        (df["quantity"] <= 0).sum()
    )

    df = df[df["quantity"] > 0].copy()

    # --------------------------------------------------------
    # item_price
    # --------------------------------------------------------

    df["item_price"] = pd.to_numeric(
        df["item_price"], errors="coerce"
    )

    invalid_price_count = int(df["item_price"].isna().sum())

    negative_price_count = int((df["item_price"] < 0).sum())

    df = df[
        df["item_price"].notna()
        & (df["item_price"] >= 0)
    ].copy()

    df["item_price"] = df["item_price"].astype(float)

    # --------------------------------------------------------
    # item_total
    # --------------------------------------------------------

    df["item_total"] = pd.to_numeric(
        df["item_total"], errors="coerce"
    )

    invalid_total_count = int(df["item_total"].isna().sum())

    negative_total_count = int((df["item_total"] < 0).sum())

    df = df[
        df["item_total"].notna()
        & (df["item_total"] >= 0)
    ].copy()

    df["item_total"] = df["item_total"].astype(float)

    # --------------------------------------------------------
    # 等式关系校验：item_total 约等于 quantity * item_price
    # --------------------------------------------------------

    expected_total = df["quantity"] * df["item_price"]

    inconsistent_count = int(
        (
            (df["item_total"] - expected_total).abs()
            > PRICE_TOLERANCE
        ).sum()
    )

    df = df[
        (df["item_total"] - expected_total).abs() <= PRICE_TOLERANCE
    ].copy()

    # --------------------------------------------------------
    # order_item_id 主键去重
    # --------------------------------------------------------

    duplicate_item_id_count = int(
        df["order_item_id"].duplicated(keep="first").sum()
    )

    df = df.drop_duplicates(
        subset=["order_item_id"], keep="first"
    ).copy()

    # --------------------------------------------------------
    # 外键完整性剔除（对照父表主键）
    # --------------------------------------------------------

    order_keys = load_parent_keys("order", "order_id")
    product_keys = load_parent_keys("product", "product_id")
    user_keys = load_parent_keys("user", "user_id")

    fk_missing_count = int(
        (
            ~df["order_id"].isin(order_keys)
            | ~df["product_id"].isin(product_keys)
            | ~df["user_id"].isin(user_keys)
        ).sum()
    )

    df = df[
        df["order_id"].isin(order_keys)
        & df["product_id"].isin(product_keys)
        & df["user_id"].isin(user_keys)
    ].copy()

    # --------------------------------------------------------
    # 清洗结果统计
    # --------------------------------------------------------

    print("=" * 60)
    print("订单明细数据清洗结果")
    print("=" * 60)
    print(f"原始订单明细：{original_count} 条")
    print(f"删除非法 order_item_id：{invalid_item_id_count} 条")
    print(f"删除非法外键格式：{invalid_fk_format_count} 条")
    print(f"删除非法 quantity：{invalid_quantity_count} 条")
    print(f"删除小数 quantity：{non_integer_quantity_count} 条")
    print(f"删除非正 quantity：{non_positive_quantity_count} 条")
    print(f"删除非法 item_price：{invalid_price_count} 条")
    print(f"删除负 item_price：{negative_price_count} 条")
    print(f"删除非法 item_total：{invalid_total_count} 条")
    print(f"删除负 item_total：{negative_total_count} 条")
    print(
        f"删除不满足 item_total=quantity*item_price："
        f"{inconsistent_count} 条"
    )
    print(f"删除重复 order_item_id：{duplicate_item_id_count} 条")
    print(f"删除外键在主键中不存在：{fk_missing_count} 条")
    print(f"最终订单明细：{len(df)} 条")

    return df.reset_index(drop=True)


# ============================================================
# 5. 建表
# ============================================================

CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS `order_item` (

    `order_item_id` BIGINT UNSIGNED NOT NULL
        COMMENT '订单明细ID',

    `order_id` BIGINT UNSIGNED NOT NULL
        COMMENT '订单ID',

    `product_id` BIGINT UNSIGNED NOT NULL
        COMMENT '商品ID',

    `user_id` BIGINT UNSIGNED NOT NULL
        COMMENT '用户ID',

    `quantity` INT UNSIGNED NOT NULL
        COMMENT '购买数量',

    `item_price` FLOAT NOT NULL
        COMMENT '商品单价',

    `item_total` FLOAT NOT NULL
        COMMENT '小计金额',

    PRIMARY KEY (`order_item_id`),

    KEY `idx_order_item_order` (`order_id`),
    KEY `idx_order_item_product` (`product_id`),
    KEY `idx_order_item_user` (`user_id`),

    CONSTRAINT `fk_order_item_order`
        FOREIGN KEY (`order_id`)
        REFERENCES `order` (`order_id`),

    CONSTRAINT `fk_order_item_product`
        FOREIGN KEY (`product_id`)
        REFERENCES `product` (`product_id`),

    CONSTRAINT `fk_order_item_user`
        FOREIGN KEY (`user_id`)
        REFERENCES `user` (`user_id`),

    CONSTRAINT `chk_order_item_quantity`
        CHECK (`quantity` > 0),

    CONSTRAINT `chk_order_item_price`
        CHECK (`item_price` >= 0),

    CONSTRAINT `chk_order_item_total`
        CHECK (`item_total` >= 0)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='订单明细表';
"""


# ============================================================
# 6. 插入 SQL
# ============================================================

INSERT_SQL = """
INSERT INTO `order_item`
(
    `order_item_id`,
    `order_id`,
    `product_id`,
    `user_id`,
    `quantity`,
    `item_price`,
    `item_total`
)
VALUES
(
    %s, %s, %s, %s, %s, %s, %s
)
"""


# ============================================================
# 7. 批量入库
# ============================================================

def import_order_items(df: pd.DataFrame):

    connection = None
    cursor = None

    try:

        connection = mysql.connector.connect(
            **DB_CONFIG
        )

        if not connection.is_connected():
            raise RuntimeError("数据库连接失败")

        cursor = connection.cursor()

        # 建表
        cursor.execute(CREATE_TABLE_SQL)

        print("[OK] order_item 表创建/检查完成")

        records = []

        for _, row in df.iterrows():

            records.append(
                (
                    int(row["order_item_id"]),
                    int(row["order_id"]),
                    int(row["product_id"]),
                    int(row["user_id"]),
                    int(row["quantity"]),
                    float(row["item_price"]),
                    float(row["item_total"]),
                )
            )

            if len(records) >= BATCH_SIZE:

                cursor.executemany(INSERT_SQL, records)

                records.clear()

        if records:

            cursor.executemany(INSERT_SQL, records)

        connection.commit()

        print("=" * 60)
        print("订单明细数据导入完成")
        print("=" * 60)
        print(f"成功插入订单明细：{len(df)} 条")
        print("[OK] 事务已提交")

    except Error as e:

        if connection is not None:
            connection.rollback()

        print("[ERROR] 数据库操作失败")
        print(f"原因：{e}")
        print("[ERROR] 事务已回滚")

        raise

    except Exception as e:

        if connection is not None:
            connection.rollback()

        print("[ERROR] 程序执行失败")
        print(f"原因：{e}")
        print("[ERROR] 事务已回滚")

        raise

    finally:

        if cursor is not None:
            cursor.close()

        if connection is not None and connection.is_connected():
            connection.close()

        print("[OK] 数据库连接已关闭")


# ============================================================
# 8. 主程序
# ============================================================

def main():

    if not CSV_PATH.exists():
        raise FileNotFoundError(
            f"找不到订单明细 CSV：{CSV_PATH}"
        )

    if not DB_CONFIG["host"]:
        raise ValueError(
            "请先填写 DB_CONFIG 中的数据库连接信息"
        )

    print(f"[INFO] 正在读取：{CSV_PATH}")

    df = pd.read_csv(CSV_PATH)

    print(f"[INFO] CSV 原始数据：{len(df)} 条")

    # 数据清洗
    df = clean_data(df)

    # 建表 + 插入
    import_order_items(df)


if __name__ == "__main__":
    main()
