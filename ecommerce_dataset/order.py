#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
订单数据清洗 + MySQL 建表 + 批量入库

输入文件：
    orders(1).csv

原始字段：
    order_id
    user_id
    order_date
    order_status
    total_amount

处理规则：
1. order_id：
   - 去掉首字母 O
   - 转换为整数
   - 作为 orders 表主键

2. user_id：
   - 去掉首字母 U
   - 转换为整数
   - 作为 orders.userid
   - 外键关联 user.userid

3. user 表不存在对应 userid：
   - 直接跳过该订单
   - 不插入数据库

4. order_date：
   - 转换为 DATETIME

5. order_status：
   - 保存订单状态
   - 当前数据状态包括：
     processing / completed / cancelled / shipped / returned

6. total_amount：
   - FLOAT

7. pay_way：
   - order_status = cancelled 时为 NULL
   - 其他状态随机生成：
       银行卡支付
       微信支付
       支付宝支付

8. 订单 orderid 重复：
   - 只保留第一条

9. 数据库操作使用事务：
   - 成功全部提交
   - 发生异常全部回滚

注意：
    user 表必须已经建立，并且 user.userid 是 BIGINT UNSIGNED PRIMARY KEY。
"""

from pathlib import Path

import pandas as pd
import mysql.connector
from mysql.connector import Error
import secrets


# ============================================================
# 1. 数据库配置
# ============================================================

DB_CONFIG = {
    "host": "127.0.0.1",          # 例如 127.0.0.1
    "port": 3306,
    "user": "root",          # 例如 root
    "password": "123589674xing",
    "database": "DSProduct",      # 例如 product
    "charset": "utf8mb4",
}

CSV_PATH = Path(__file__).with_name("orders.csv")

BATCH_SIZE = 500

PAY_WAYS = [
    "银行卡支付",
    "微信支付",
    "支付宝支付",
]

ALLOWED_STATUS = {
    "processing",
    "completed",
    "cancelled",
    "shipped",
    "returned",
}


# ============================================================
# 2. ID 转换
# ============================================================

def convert_prefixed_id(value, prefix: str):
    """
    例如：
        O00000001 -> 1
        U009310    -> 9310

    如果格式不正确，返回 None。
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
# 3. 数据清洗
# ============================================================

def clean_data(df: pd.DataFrame) -> pd.DataFrame:
    required_columns = {
        "order_id",
        "user_id",
        "order_date",
        "order_status",
        "total_amount",
    }

    missing_columns = required_columns - set(df.columns)

    if missing_columns:
        raise ValueError(
            f"CSV 缺少必要字段：{sorted(missing_columns)}"
        )

    original_count = len(df)

    # --------------------------------------------------------
    # order_id：O00000001 -> 1
    # --------------------------------------------------------
    df["orderid"] = df["order_id"].apply(
        lambda x: convert_prefixed_id(x, "O")
    )

    # --------------------------------------------------------
    # user_id：U009310 -> 9310
    # --------------------------------------------------------
    df["userid"] = df["user_id"].apply(
        lambda x: convert_prefixed_id(x, "U")
    )

    invalid_orderid = df["orderid"].isna()
    invalid_userid = df["userid"].isna()

    # orderid / userid 必须有效
    invalid_id_count = int(
        (invalid_orderid | invalid_userid).sum()
    )

    df = df[
        ~invalid_orderid & ~invalid_userid
    ].copy()

    df["orderid"] = df["orderid"].astype("int64")
    df["userid"] = df["userid"].astype("int64")

    # --------------------------------------------------------
    # orderid 是主键，所以重复 orderid 只保留第一条
    # --------------------------------------------------------
    duplicate_orderid_count = int(
        df["orderid"].duplicated(keep="first").sum()
    )

    df = df.drop_duplicates(
        subset=["orderid"],
        keep="first"
    ).copy()

    # --------------------------------------------------------
    # order_date -> datetime
    # --------------------------------------------------------
    df["order_date"] = pd.to_datetime(
        df["order_date"],
        errors="coerce"
    )

    invalid_date_count = int(
        df["order_date"].isna().sum()
    )

    df = df[df["order_date"].notna()].copy()

    # --------------------------------------------------------
    # order_status
    # --------------------------------------------------------
    df["order_status"] = (
        df["order_status"]
        .astype("string")
        .str.strip()
        .str.lower()
    )

    invalid_status_count = int(
        (~df["order_status"].isin(ALLOWED_STATUS)).sum()
    )

    df = df[
        df["order_status"].isin(ALLOWED_STATUS)
    ].copy()

    # --------------------------------------------------------
    # total_amount -> float
    # --------------------------------------------------------
    df["total_amount"] = pd.to_numeric(
        df["total_amount"],
        errors="coerce"
    )

    invalid_amount_count = int(
        df["total_amount"].isna().sum()
    )

    df = df[
        df["total_amount"].notna()
    ].copy()

    # 金额不能为负
    negative_amount_count = int(
        (df["total_amount"] < 0).sum()
    )

    df = df[
        df["total_amount"] >= 0
    ].copy()

    print("=" * 60)
    print("订单数据清洗结果")
    print("=" * 60)
    print(f"原始订单：{original_count} 条")
    print(f"删除非法 orderid/userid：{invalid_id_count} 条")
    print(f"删除重复 orderid：{duplicate_orderid_count} 条")
    print(f"删除非法 order_date：{invalid_date_count} 条")
    print(f"删除非法 order_status：{invalid_status_count} 条")
    print(f"删除非法 total_amount：{invalid_amount_count} 条")
    print(f"删除负金额：{negative_amount_count} 条")
    print(f"清洗后：{len(df)} 条")

    return df.reset_index(drop=True)


# ============================================================
# 4. 查询 user 表中实际存在的 userid
# ============================================================

def get_existing_userids(cursor) -> set:
    """
    从 user 表读取所有 userid。
    后续只插入存在对应用户的订单。
    """

    cursor.execute(
        "SELECT `user_id` FROM `user`"
    )

    rows = cursor.fetchall()

    return {
        int(row[0])
        for row in rows
    }


# ============================================================
# 5. 生成 pay_way
# ============================================================

def generate_pay_way(status: str):
    """
    cancelled -> NULL
    其他状态 -> 三种支付方式随机选择一种
    """

    if status == "cancelled":
        return None

    return secrets.choice(PAY_WAYS)


# ============================================================
# 6. 建立 order 表
# ============================================================

CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS `order` (
    `order_id` BIGINT UNSIGNED NOT NULL
        COMMENT '订单ID',

    `user_id` BIGINT UNSIGNED NOT NULL
        COMMENT '用户ID',

    `order_date` DATETIME NOT NULL
        COMMENT '下单时间',

    `order_status` ENUM(
        'processing',
        'completed',
        'cancelled',
        'shipped',
        'returned'
    ) NOT NULL
        COMMENT '订单状态',

    `total_amount` FLOAT NOT NULL
        COMMENT '订单总金额',

    `pay_way` ENUM(
        '银行卡支付',
        '微信支付',
        '支付宝支付'
    ) NULL
        COMMENT '支付方式，取消订单为NULL',

    PRIMARY KEY (`order_id`),

    KEY `idx_order_userid` (`user_id`),

    CONSTRAINT `fk_order_user`
        FOREIGN KEY (`user_id`)
        REFERENCES `user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT `chk_order_pay_way`
        CHECK (
            (`order_status` = 'cancelled' AND `pay_way` IS NULL)
            OR
            (`order_status` <> 'cancelled' AND `pay_way` IS NOT NULL)
        )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='订单表';
"""


# ============================================================
# 7. 插入 SQL
# ============================================================

INSERT_SQL = """
INSERT INTO `order`
(
    `order_id`,
    `user_id`,
    `order_date`,
    `order_status`,
    `total_amount`,
    `pay_way`
)
VALUES
(
    %s, %s, %s, %s, %s, %s
)
"""


# ============================================================
# 8. 数据库处理
# ============================================================

def import_orders(df: pd.DataFrame):

    connection = None
    cursor = None

    try:
        connection = mysql.connector.connect(**DB_CONFIG)

        if not connection.is_connected():
            raise RuntimeError("数据库连接失败")

        cursor = connection.cursor()

        # ----------------------------------------------------
        # 建表
        # ----------------------------------------------------
        cursor.execute(CREATE_TABLE_SQL)

        print("[OK] order 表创建/检查完成")

        # ----------------------------------------------------
        # 获取 user 表真实存在的 userid
        # ----------------------------------------------------
        existing_userids = get_existing_userids(cursor)

        print(
            f"[INFO] user 表中存在 {len(existing_userids)} 个用户"
        )

        # ----------------------------------------------------
        # 外键过滤
        # user.userid 不存在 -> 直接跳过
        # ----------------------------------------------------
        before_fk = len(df)

        df = df[
            df["userid"].isin(existing_userids)
        ].copy()

        skipped_fk_count = before_fk - len(df)

        print(
            f"[INFO] 因 user.userid 不存在而跳过："
            f"{skipped_fk_count} 条"
        )

        # ----------------------------------------------------
        # 生成 pay_way
        # ----------------------------------------------------
        df["pay_way"] = df["order_status"].apply(
            generate_pay_way
        )

        # ----------------------------------------------------
        # 构造批量插入数据
        # ----------------------------------------------------
        records = []

        for _, row in df.iterrows():

            order_date = row["order_date"]

            # pandas Timestamp -> Python datetime
            if pd.notna(order_date):
                order_date = order_date.to_pydatetime()

            records.append(
                (
                    int(row["orderid"]),
                    int(row["userid"]),
                    order_date,
                    str(row["order_status"]),
                    float(row["total_amount"]),
                    row["pay_way"],
                )
            )

            if len(records) >= BATCH_SIZE:
                cursor.executemany(
                    INSERT_SQL,
                    records
                )
                records.clear()

        if records:
            cursor.executemany(
                INSERT_SQL,
                records
            )

        connection.commit()

        print("=" * 60)
        print("订单数据导入完成")
        print("=" * 60)
        print(f"最终插入订单：{len(df)} 条")
        print(f"跳过外键不存在订单：{skipped_fk_count} 条")
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

        if (
            connection is not None
            and connection.is_connected()
        ):
            connection.close()

        print("[OK] 数据库连接已关闭")


# ============================================================
# 9. 主程序
# ============================================================

def main():

    if not CSV_PATH.exists():
        raise FileNotFoundError(
            f"找不到订单 CSV：{CSV_PATH}"
        )

    if not DB_CONFIG["host"]:
        raise ValueError(
            "请先填写 DB_CONFIG 中的数据库连接信息"
        )

    print(f"[INFO] 正在读取：{CSV_PATH}")

    df = pd.read_csv(CSV_PATH)

    print(f"[INFO] CSV 原始数据：{len(df)} 条")

    # 1. 清洗
    df = clean_data(df)

    # 2. 建表 + 外键过滤 + 生成 pay_way + 入库
    import_orders(df)


if __name__ == "__main__":
    main()
