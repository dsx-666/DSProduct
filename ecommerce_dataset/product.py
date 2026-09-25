#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
商品数据清洗 + MySQL 建表 + 批量入库

输入：
    products.csv

原始字段：
    product_id
    product_name
    category
    brand
    price
    rating

处理规则：
1. product_id：
   - 去掉首字母 P
   - 转换为整数
   - 作为 product 表主键
   例如：P000001 -> 1

2. product_name：
   - 不能为空
   - 必须唯一
   - 重复商品名只保留第一条

3. category / brand：
   - 去除首尾空格
   - 不能为空

4. price：
   - 清洗并转换为浮点数
   - 不能为空
   - 不能小于 0

5. rating：
   - 清洗并转换为浮点数
   - 不能为空
   - 必须在 0~5 之间

6. 使用事务批量插入：
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
    "user": "root",          # 例如：root
    "password": "123589674xing",
    "database": "DSProduct",      # 例如：product
    "charset": "utf8mb4",
}

CSV_PATH = Path(__file__).with_name("products.csv")

BATCH_SIZE = 500


# ============================================================
# 2. product_id 转换
# ============================================================

def convert_product_id(value):
    """
    P000001 -> 1
    P000100 -> 100

    格式不正确返回 None。
    """

    if pd.isna(value):
        return None

    value = str(value).strip()

    if not value.startswith("P"):
        return None

    number_part = value[1:]

    if not number_part.isdigit():
        return None

    return int(number_part)


# ============================================================
# 3. 数据清洗
# ============================================================

def clean_data(df: pd.DataFrame) -> pd.DataFrame:

    required_columns = {
        "product_id",
        "product_name",
        "category",
        "brand",
        "price",
        "rating",
    }

    missing_columns = required_columns - set(df.columns)

    if missing_columns:
        raise ValueError(
            f"CSV 缺少必要字段：{sorted(missing_columns)}"
        )

    original_count = len(df)

    # --------------------------------------------------------
    # product_id
    # --------------------------------------------------------

    df["product_id"] = df["product_id"].apply(
        convert_product_id
    )

    invalid_product_id_count = int(
        df["product_id"].isna().sum()
    )

    df = df[
        df["product_id"].notna()
    ].copy()

    df["product_id"] = df["product_id"].astype("int64")

    # --------------------------------------------------------
    # product_name
    # --------------------------------------------------------

    df["product_name"] = (
        df["product_name"]
        .astype("string")
        .str.strip()
    )

    invalid_product_name_count = int(
        df["product_name"].isna().sum()
        + (df["product_name"] == "").sum()
    )

    df = df[
        df["product_name"].notna()
        & df["product_name"].ne("")
    ].copy()

    # product_name 必须唯一，重复保留第一条
    duplicate_product_name_count = int(
        df["product_name"].duplicated(
            keep="first"
        ).sum()
    )

    df = df.drop_duplicates(
        subset=["product_name"],
        keep="first"
    ).copy()

    # --------------------------------------------------------
    # category / brand
    # --------------------------------------------------------

    for column in ["category", "brand"]:

        df[column] = (
            df[column]
            .astype("string")
            .str.strip()
        )

    invalid_category_brand_count = int(
        (
            df["category"].isna()
            | df["category"].eq("")
            | df["brand"].isna()
            | df["brand"].eq("")
        ).sum()
    )

    df = df[
        df["category"].notna()
        & df["category"].ne("")
        & df["brand"].notna()
        & df["brand"].ne("")
    ].copy()

    # --------------------------------------------------------
    # price
    # --------------------------------------------------------

    df["price"] = pd.to_numeric(
        df["price"],
        errors="coerce"
    )

    invalid_price_count = int(
        df["price"].isna().sum()
    )

    negative_price_count = int(
        (df["price"] < 0).sum()
    )

    df = df[
        df["price"].notna()
        & (df["price"] >= 0)
    ].copy()

    df["price"] = df["price"].astype(float)

    # --------------------------------------------------------
    # rating
    # --------------------------------------------------------

    df["rating"] = pd.to_numeric(
        df["rating"],
        errors="coerce"
    )

    invalid_rating_count = int(
        df["rating"].isna().sum()
    )

    out_of_range_rating_count = int(
        (
            (df["rating"] < 0)
            | (df["rating"] > 5)
        ).sum()
    )

    df = df[
        df["rating"].notna()
        & (df["rating"] >= 0)
        & (df["rating"] <= 5)
    ].copy()

    df["rating"] = df["rating"].astype(float)

    # --------------------------------------------------------
    # product_id 重复
    # --------------------------------------------------------

    duplicate_product_id_count = int(
        df["product_id"].duplicated(
            keep="first"
        ).sum()
    )

    df = df.drop_duplicates(
        subset=["product_id"],
        keep="first"
    ).copy()

    print("=" * 60)
    print("商品数据清洗结果")
    print("=" * 60)
    print(f"原始商品：{original_count} 条")
    print(f"删除非法 product_id：{invalid_product_id_count} 条")
    print(f"删除空 product_name：{invalid_product_name_count} 条")
    print(f"删除重复 product_name：{duplicate_product_name_count} 条")
    print(
        f"删除空 category/brand："
        f"{invalid_category_brand_count} 条"
    )
    print(f"删除非法 price：{invalid_price_count} 条")
    print(f"删除负 price：{negative_price_count} 条")
    print(f"删除非法 rating：{invalid_rating_count} 条")
    print(
        f"删除超出 0~5 范围的 rating："
        f"{out_of_range_rating_count} 条"
    )
    print(
        f"删除重复 product_id："
        f"{duplicate_product_id_count} 条"
    )
    print(f"最终商品：{len(df)} 条")

    return df.reset_index(drop=True)


# ============================================================
# 4. 建表
# ============================================================

CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS `product` (

    `product_id` BIGINT UNSIGNED NOT NULL
        COMMENT '商品ID',

    `product_name` VARCHAR(255) NOT NULL
        COMMENT '商品名称',

    `category` VARCHAR(100) NOT NULL
        COMMENT '商品类别',

    `brand` VARCHAR(100) NOT NULL
        COMMENT '品牌',

    `price` FLOAT NOT NULL
        COMMENT '商品价格',

    `rating` FLOAT NOT NULL
        COMMENT '商品评分',

    PRIMARY KEY (`product_id`),

    UNIQUE KEY `uk_product_name` (`product_name`),

    CONSTRAINT `chk_product_price`
        CHECK (`price` >= 0),

    CONSTRAINT `chk_product_rating`
        CHECK (`rating` >= 0 AND `rating` <= 5)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='商品表';
"""


# ============================================================
# 5. 插入 SQL
# ============================================================

INSERT_SQL = """
INSERT INTO `product`
(
    `product_id`,
    `product_name`,
    `category`,
    `brand`,
    `price`,
    `rating`
)
VALUES
(
    %s, %s, %s, %s, %s, %s
)
"""


# ============================================================
# 6. 批量入库
# ============================================================

def import_products(df: pd.DataFrame):

    connection = None
    cursor = None

    try:

        connection = mysql.connector.connect(
            **DB_CONFIG
        )

        if not connection.is_connected():
            raise RuntimeError(
                "数据库连接失败"
            )

        cursor = connection.cursor()

        # 建表
        cursor.execute(
            CREATE_TABLE_SQL
        )

        print(
            "[OK] product 表创建/检查完成"
        )

        records = []

        for _, row in df.iterrows():

            records.append(
                (
                    int(row["product_id"]),
                    str(row["product_name"]),
                    str(row["category"]),
                    str(row["brand"]),
                    float(row["price"]),
                    float(row["rating"]),
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
        print("商品数据导入完成")
        print("=" * 60)
        print(
            f"成功插入商品：{len(df)} 条"
        )
        print("[OK] 事务已提交")

    except Error as e:

        if connection is not None:
            connection.rollback()

        print(
            "[ERROR] 数据库操作失败"
        )
        print(f"原因：{e}")
        print("[ERROR] 事务已回滚")

        raise

    except Exception as e:

        if connection is not None:
            connection.rollback()

        print(
            "[ERROR] 程序执行失败"
        )
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

        print(
            "[OK] 数据库连接已关闭"
        )


# ============================================================
# 7. 主程序
# ============================================================

def main():

    if not CSV_PATH.exists():

        raise FileNotFoundError(
            f"找不到商品 CSV：{CSV_PATH}"
        )

    if not DB_CONFIG["host"]:

        raise ValueError(
            "请先填写 DB_CONFIG 中的数据库连接信息"
        )

    print(
        f"[INFO] 正在读取：{CSV_PATH}"
    )

    df = pd.read_csv(
        CSV_PATH
    )

    print(
        f"[INFO] CSV 原始数据：{len(df)} 条"
    )

    # 数据清洗
    df = clean_data(df)

    # 建表 + 插入
    import_products(df)


if __name__ == "__main__":
    main()
