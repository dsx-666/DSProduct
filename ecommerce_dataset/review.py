#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
评价数据清洗 + MySQL 建表 + 批量入库

输入：
    reviews.csv

原始字段：
    review_id
    order_id
    product_id
    user_id
    rating
    review_text
    review_date

处理规则：
1. review_id：
   - 去掉首字母 R
   - 转换为整数
   - 作为 review 表主键
   例如：R00000528 -> 528

2. order_id：
   - 去掉首字母 O -> 整数
   - 作为外键，引用 order(order_id)

3. product_id：
   - 去掉首字母 P -> 整数
   - 作为外键，引用 product(product_id)

4. user_id：
   - 去掉首字母 U -> 整数
   - 作为外键，引用 user(user_id)

5. rating：
   - 清洗并转换为整数
   - 必须在 0~5 之间

6. review_date：
   - 解析为 datetime，不能为空

7. 外键完整性：
   - order_id / product_id / user_id 必须能在
     order / product / user 对应表主键中找到，
     找不到的记录整行剔除

8. review_id：
   - 必须唯一，重复只保留第一条

9. 使用事务批量插入：
   - 全部成功 -> COMMIT
   - 出现异常 -> ROLLBACK

数据库配置按需要自行填写。
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

CSV_PATH = Path(__file__).with_name("reviews.csv")

BATCH_SIZE = 500


# ============================================================
# 2. 通用首字母前缀 ID 转换
# ============================================================

def convert_id(value, prefix):
    """
    R00000528 -> 528   (prefix='R')
    O00000237 -> 237   (prefix='O')
    P001326   -> 1326  (prefix='P')
    U001094   -> 1094  (prefix='U')

    格式不正确返回 None。
    """

    if pd.isna(value):
        return None

    value = str(value).strip()

    if not value.startswith(prefix):
        return None

    number_part = value[len(prefix):]

    if not number_part.isdigit():
        return None

    return int(number_part)


# ============================================================
# 3. 读取父表主键（用于外键完整性校验）
# ============================================================

def load_parent_keys(table, key_column):
    """
    查询某张父表已有的主键集合，返回 set。
    表不存在 / 查询失败时返回空集合，
    后续会把引用不到父表的记录全部剔除。
    """

    connection = None
    cursor = None

    try:

        connection = mysql.connector.connect(**DB_CONFIG)

        cursor = connection.cursor()

        cursor.execute(
            f"SELECT `{key_column}` FROM `{table}`"
        )

        return {int(row[0]) for row in cursor.fetchall()}

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
        "review_id",
        "order_id",
        "product_id",
        "user_id",
        "rating",
        "review_text",
        "review_date",
    }

    missing_columns = required_columns - set(df.columns)

    if missing_columns:
        raise ValueError(
            f"CSV 缺少必要字段：{sorted(missing_columns)}"
        )

    original_count = len(df)

    # --------------------------------------------------------
    # review_id（主键）
    # --------------------------------------------------------

    df["review_id"] = df["review_id"].apply(
        lambda v: convert_id(v, "R")
    )

    invalid_review_id_count = int(df["review_id"].isna().sum())

    df = df[df["review_id"].notna()].copy()
    df["review_id"] = df["review_id"].astype("int64")

    # --------------------------------------------------------
    # order_id / product_id / user_id（外键）
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

    invalid_order_id_count = int(df["order_id"].isna().sum())
    invalid_product_id_count = int(df["product_id"].isna().sum())
    invalid_user_id_count = int(df["user_id"].isna().sum())

    df = df[
        df["order_id"].notna()
        & df["product_id"].notna()
        & df["user_id"].notna()
    ].copy()

    df["order_id"] = df["order_id"].astype("int64")
    df["product_id"] = df["product_id"].astype("int64")
    df["user_id"] = df["user_id"].astype("int64")

    # --------------------------------------------------------
    # rating（整型，0~5）
    # --------------------------------------------------------

    df["rating"] = pd.to_numeric(df["rating"], errors="coerce")

    invalid_rating_count = int(df["rating"].isna().sum())

    # 评分应为整数：出现小数（如 4.5）按非法处理
    non_integer_rating_count = int(
        (df["rating"].notna() & (df["rating"] % 1 != 0)).sum()
    )

    out_of_range_rating_count = int(
        (
            df["rating"].notna()
            & ((df["rating"] < 0) | (df["rating"] > 5))
        ).sum()
    )

    df = df[
        df["rating"].notna()
        & (df["rating"] % 1 == 0)
        & (df["rating"] >= 0)
        & (df["rating"] <= 5)
    ].copy()

    df["rating"] = df["rating"].astype("int64")

    # --------------------------------------------------------
    # review_date（时间数据）
    # --------------------------------------------------------

    df["review_date"] = pd.to_datetime(
        df["review_date"],
        errors="coerce"
    )

    invalid_review_date_count = int(df["review_date"].isna().sum())

    df = df[df["review_date"].notna()].copy()

    # --------------------------------------------------------
    # review_text（去除首尾空格，允许为空）
    # --------------------------------------------------------

    df["review_text"] = (
        df["review_text"]
        .astype("string")
        .str.strip()
        .fillna("")
    )

    # --------------------------------------------------------
    # 外键完整性校验：
    # 外键值必须在对应父表主键中存在，否则整行剔除
    # --------------------------------------------------------

    order_keys = load_parent_keys("order", "order_id")
    product_keys = load_parent_keys("product", "product_id")
    user_keys = load_parent_keys("user", "user_id")

    bad_order_count = int((~df["order_id"].isin(order_keys)).sum())
    bad_product_count = int(
        (~df["product_id"].isin(product_keys)).sum()
    )
    bad_user_count = int((~df["user_id"].isin(user_keys)).sum())

    df = df[
        df["order_id"].isin(order_keys)
        & df["product_id"].isin(product_keys)
        & df["user_id"].isin(user_keys)
    ].copy()

    # --------------------------------------------------------
    # review_id 重复
    # --------------------------------------------------------

    duplicate_review_id_count = int(
        df["review_id"].duplicated(keep="first").sum()
    )

    df = df.drop_duplicates(
        subset=["review_id"],
        keep="first"
    ).copy()

    print("=" * 60)
    print("评价数据清洗结果")
    print("=" * 60)
    print(f"原始评价：{original_count} 条")
    print(f"删除非法 review_id：{invalid_review_id_count} 条")
    print(f"删除非法 order_id：{invalid_order_id_count} 条")
    print(f"删除非法 product_id：{invalid_product_id_count} 条")
    print(f"删除非法 user_id：{invalid_user_id_count} 条")
    print(f"删除非法 rating：{invalid_rating_count} 条")
    print(f"删除非整数 rating：{non_integer_rating_count} 条")
    print(f"删除超出 0~5 范围的 rating：{out_of_range_rating_count} 条")
    print(f"删除非法 review_date：{invalid_review_date_count} 条")
    print(f"删除 order_id 在 order 表不存在：{bad_order_count} 条")
    print(f"删除 product_id 在 product 表不存在：{bad_product_count} 条")
    print(f"删除 user_id 在 user 表不存在：{bad_user_count} 条")
    print(f"删除重复 review_id：{duplicate_review_id_count} 条")
    print(f"最终评价：{len(df)} 条")

    return df.reset_index(drop=True)


# ============================================================
# 5. 建表
# ============================================================

CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS `review` (

    `review_id` BIGINT UNSIGNED NOT NULL
        COMMENT '评价ID',

    `order_id` BIGINT UNSIGNED NOT NULL
        COMMENT '订单ID（外键）',

    `product_id` BIGINT UNSIGNED NOT NULL
        COMMENT '商品ID（外键）',

    `user_id` BIGINT UNSIGNED NOT NULL
        COMMENT '用户ID（外键）',

    `rating` TINYINT UNSIGNED NOT NULL
        COMMENT '评分（整型 0~5）',

    `review_text` TEXT NULL
        COMMENT '评价内容',

    `review_date` DATETIME NOT NULL
        COMMENT '评价时间',

    PRIMARY KEY (`review_id`),

    KEY `idx_review_order` (`order_id`),
    KEY `idx_review_product` (`product_id`),
    KEY `idx_review_user` (`user_id`),

    CONSTRAINT `chk_review_rating`
        CHECK (`rating` >= 0 AND `rating` <= 5),

    CONSTRAINT `fk_review_order`
        FOREIGN KEY (`order_id`) REFERENCES `order` (`order_id`),

    CONSTRAINT `fk_review_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`),

    CONSTRAINT `fk_review_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='评价表';
"""


# ============================================================
# 6. 插入 SQL
# ============================================================

INSERT_SQL = """
INSERT INTO `review`
(
    `review_id`,
    `order_id`,
    `product_id`,
    `user_id`,
    `rating`,
    `review_text`,
    `review_date`
)
VALUES
(
    %s, %s, %s, %s, %s, %s, %s
)
"""


# ============================================================
# 7. 批量入库
# ============================================================

def import_reviews(df: pd.DataFrame):

    connection = None
    cursor = None

    try:

        connection = mysql.connector.connect(**DB_CONFIG)

        if not connection.is_connected():
            raise RuntimeError("数据库连接失败")

        cursor = connection.cursor()

        # 建表
        cursor.execute(CREATE_TABLE_SQL)

        print("[OK] review 表创建/检查完成")

        records = []

        for _, row in df.iterrows():

            review_text = row["review_text"]
            review_text = None if review_text == "" else str(review_text)

            records.append(
                (
                    int(row["review_id"]),
                    int(row["order_id"]),
                    int(row["product_id"]),
                    int(row["user_id"]),
                    int(row["rating"]),
                    review_text,
                    row["review_date"].to_pydatetime(),
                )
            )

            if len(records) >= BATCH_SIZE:
                cursor.executemany(INSERT_SQL, records)
                records.clear()

        if records:
            cursor.executemany(INSERT_SQL, records)

        connection.commit()

        print("=" * 60)
        print("评价数据导入完成")
        print("=" * 60)
        print(f"成功插入评价：{len(df)} 条")
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
        raise FileNotFoundError(f"找不到评价 CSV：{CSV_PATH}")

    if not DB_CONFIG["host"]:
        raise ValueError("请先填写 DB_CONFIG 中的数据库连接信息")

    print(f"[INFO] 正在读取：{CSV_PATH}")

    df = pd.read_csv(CSV_PATH)

    print(f"[INFO] CSV 原始数据：{len(df)} 条")

    # 数据清洗
    df = clean_data(df)

    # 建表 + 插入
    import_reviews(df)


if __name__ == "__main__":
    main()
