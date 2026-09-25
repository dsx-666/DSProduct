#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
评价事件数据清洗 + MySQL 建表 + 批量入库

输入：
    events.csv

原始字段：
    event_id
    user_id
    product_id
    event_type
    event_timestamp

处理规则：
1. event_id：
   - 去掉首字母 E
   - 转换为整数
   - 作为 event 表主键
   例如：E00000001 -> 1

2. user_id：
   - 去掉首字母 U
   - 转换为整数
   - 作为外键，引用 user(user_id)

3. product_id：
   - 去掉首字母 P
   - 转换为整数
   - 作为外键，引用 product(product_id)

4. event_type：
   - 去除首尾空格
   - 不能为空
   （注：event_type 实际是事件类别文本，如 cart/view，并非时间数据）

5. event_timestamp：
   - 解析为时间数据（DATETIME）
   - 不能为空，解析失败整行剔除

6. 外键完整性：
   - user_id 必须存在于 user(user_id)
   - product_id 必须存在于 product(product_id)
   - 任一外键在对应父表主键中不存在，整行剔除

7. event_id 去重：
   - 重复主键保留第一条

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
    'host': '127.0.0.1',          # 例如：127.0.0.1
    'port': 3306,
    'user': 'root',          # 例如：root
    'password': '123589674xing',
    'database': 'DSProduct',      # 例如：product
    'charset': 'utf8mb4',
}

CSV_PATH = Path(__file__).with_name('events.csv')

BATCH_SIZE = 500


# ============================================================
# 2. 通用 ID 转换（去掉首字母）
# ============================================================

def convert_id(value, prefix):
    """
    去掉首字母前缀并转换为整数。
    例如 prefix='E': E00000001 -> 1
         prefix='U': U009798   -> 9798
         prefix='P': P001393   -> 1393

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
# 3. 从父表读取主键集合
# ============================================================

def load_parent_keys(cursor, table, key_column):
    """
    读取父表指定主键列的全部取值，返回 set。
    父表不存在或查询失败时返回空集合（即所有外键都视为非法）。
    """

    try:
        cursor.execute(
            'SELECT `{key_column}` FROM `{table}`'.format(
                key_column=key_column,
                table=table,
            )
        )

        rows = cursor.fetchall()

        return {int(row[0]) for row in rows}

    except Error as e:
        print(
            '[WARN] 读取父表 {t}({c}) 失败：{e}'.format(
                t=table, c=key_column, e=e
            )
        )
        print('[WARN] 该父表主键集合按空集合处理')

        return set()


# ============================================================
# 4. 数据清洗
# ============================================================

def clean_data(
    df: pd.DataFrame,
    user_keys: set,
    product_keys: set,
) -> pd.DataFrame:

    required_columns = {
        'event_id',
        'user_id',
        'product_id',
        'event_type',
        'event_timestamp',
    }

    missing_columns = required_columns - set(df.columns)

    if missing_columns:
        raise ValueError(
            'CSV 缺少必要字段：{m}'.format(
                m=sorted(missing_columns)
            )
        )

    original_count = len(df)

    # --------------------------------------------------------
    # event_id（主键）
    # --------------------------------------------------------

    df['event_id'] = df['event_id'].apply(
        lambda v: convert_id(v, 'E')
    )

    invalid_event_id_count = int(
        df['event_id'].isna().sum()
    )

    df = df[
        df['event_id'].notna()
    ].copy()

    df['event_id'] = df['event_id'].astype('int64')

    # --------------------------------------------------------
    # user_id（外键）
    # --------------------------------------------------------

    df['user_id'] = df['user_id'].apply(
        lambda v: convert_id(v, 'U')
    )

    invalid_user_id_count = int(
        df['user_id'].isna().sum()
    )

    df = df[
        df['user_id'].notna()
    ].copy()

    df['user_id'] = df['user_id'].astype('int64')

    # --------------------------------------------------------
    # product_id（外键）
    # --------------------------------------------------------

    df['product_id'] = df['product_id'].apply(
        lambda v: convert_id(v, 'P')
    )

    invalid_product_id_count = int(
        df['product_id'].isna().sum()
    )

    df = df[
        df['product_id'].notna()
    ].copy()

    df['product_id'] = df['product_id'].astype('int64')

    # --------------------------------------------------------
    # event_type
    # --------------------------------------------------------

    df['event_type'] = (
        df['event_type']
        .astype('string')
        .str.strip()
    )

    invalid_event_type_count = int(
        df['event_type'].isna().sum()
        + (df['event_type'] == '').sum()
    )

    df = df[
        df['event_type'].notna()
        & df['event_type'].ne('')
    ].copy()

    # --------------------------------------------------------
    # event_timestamp
    # --------------------------------------------------------

    df['event_timestamp'] = pd.to_datetime(
        df['event_timestamp'],
        errors='coerce',
    )

    invalid_timestamp_count = int(
        df['event_timestamp'].isna().sum()
    )

    df = df[
        df['event_timestamp'].notna()
    ].copy()

    # --------------------------------------------------------
    # 外键完整性：user_id 必须在 user(user_id) 中
    # --------------------------------------------------------

    bad_user_fk_count = int(
        (~df['user_id'].isin(user_keys)).sum()
    )

    df = df[
        df['user_id'].isin(user_keys)
    ].copy()

    # --------------------------------------------------------
    # 外键完整性：product_id 必须在 product(product_id) 中
    # --------------------------------------------------------

    bad_product_fk_count = int(
        (~df['product_id'].isin(product_keys)).sum()
    )

    df = df[
        df['product_id'].isin(product_keys)
    ].copy()

    # --------------------------------------------------------
    # event_id 去重
    # --------------------------------------------------------

    duplicate_event_id_count = int(
        df['event_id'].duplicated(
            keep='first'
        ).sum()
    )

    df = df.drop_duplicates(
        subset=['event_id'],
        keep='first',
    ).copy()

    print('=' * 60)
    print('评价事件数据清洗结果')
    print('=' * 60)
    print('原始事件：{n} 条'.format(n=original_count))
    print('删除非法 event_id：{n} 条'.format(n=invalid_event_id_count))
    print('删除非法 user_id：{n} 条'.format(n=invalid_user_id_count))
    print('删除非法 product_id：{n} 条'.format(n=invalid_product_id_count))
    print('删除空 event_type：{n} 条'.format(n=invalid_event_type_count))
    print('删除非法 event_timestamp：{n} 条'.format(n=invalid_timestamp_count))
    print('删除 user_id 在 user 表不存在：{n} 条'.format(n=bad_user_fk_count))
    print('删除 product_id 在 product 表不存在：{n} 条'.format(n=bad_product_fk_count))
    print('删除重复 event_id：{n} 条'.format(n=duplicate_event_id_count))
    print('最终事件：{n} 条'.format(n=len(df)))

    return df.reset_index(drop=True)


# ============================================================
# 5. 建表
# ============================================================

CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS `event` (

    `event_id` BIGINT UNSIGNED NOT NULL
        COMMENT '事件ID',

    `user_id` BIGINT UNSIGNED NOT NULL
        COMMENT '用户ID',

    `product_id` BIGINT UNSIGNED NOT NULL
        COMMENT '商品ID',

    `event_type` VARCHAR(50) NOT NULL
        COMMENT '事件类型',

    `event_timestamp` DATETIME NOT NULL
        COMMENT '事件发生时间',

    PRIMARY KEY (`event_id`),

    KEY `idx_event_user_id` (`user_id`),

    KEY `idx_event_product_id` (`product_id`),

    CONSTRAINT `fk_event_user`
        FOREIGN KEY (`user_id`)
        REFERENCES `user` (`user_id`),

    CONSTRAINT `fk_event_product`
        FOREIGN KEY (`product_id`)
        REFERENCES `product` (`product_id`)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='评价事件表';
"""


# ============================================================
# 6. 插入 SQL
# ============================================================

INSERT_SQL = """
INSERT INTO `event`
(
    `event_id`,
    `user_id`,
    `product_id`,
    `event_type`,
    `event_timestamp`
)
VALUES
(
    %s, %s, %s, %s, %s
)
"""


# ============================================================
# 7. 批量入库
# ============================================================

def import_events(df: pd.DataFrame):

    connection = None
    cursor = None

    try:

        connection = mysql.connector.connect(
            **DB_CONFIG
        )

        if not connection.is_connected():
            raise RuntimeError(
                '数据库连接失败'
            )

        cursor = connection.cursor()

        # 读取父表主键集合，用于外键完整性校验
        user_keys = load_parent_keys(
            cursor, 'user', 'user_id'
        )

        product_keys = load_parent_keys(
            cursor, 'product', 'product_id'
        )

        print(
            '[INFO] user 表主键：{n} 个'.format(n=len(user_keys))
        )
        print(
            '[INFO] product 表主键：{n} 个'.format(n=len(product_keys))
        )

        # 数据清洗（含外键完整性过滤）
        df = clean_data(
            df,
            user_keys,
            product_keys,
        )

        # 建表
        cursor.execute(
            CREATE_TABLE_SQL
        )

        print(
            '[OK] event 表创建/检查完成'
        )

        records = []

        for _, row in df.iterrows():

            records.append(
                (
                    int(row['event_id']),
                    int(row['user_id']),
                    int(row['product_id']),
                    str(row['event_type']),
                    row['event_timestamp'].to_pydatetime(),
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

        print('=' * 60)
        print('评价事件数据导入完成')
        print('=' * 60)
        print(
            '成功插入事件：{n} 条'.format(n=len(df))
        )
        print('[OK] 事务已提交')

    except Error as e:

        if connection is not None:
            connection.rollback()

        print(
            '[ERROR] 数据库操作失败'
        )
        print('原因：{e}'.format(e=e))
        print('[ERROR] 事务已回滚')

        raise

    except Exception as e:

        if connection is not None:
            connection.rollback()

        print(
            '[ERROR] 程序执行失败'
        )
        print('原因：{e}'.format(e=e))
        print('[ERROR] 事务已回滚')

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
            '[OK] 数据库连接已关闭'
        )


# ============================================================
# 8. 主程序
# ============================================================

def main():

    if not CSV_PATH.exists():

        raise FileNotFoundError(
            '找不到事件 CSV：{p}'.format(p=CSV_PATH)
        )

    if not DB_CONFIG['host']:

        raise ValueError(
            '请先填写 DB_CONFIG 中的数据库连接信息'
        )

    print(
        '[INFO] 正在读取：{p}'.format(p=CSV_PATH)
    )

    df = pd.read_csv(
        CSV_PATH
    )

    print(
        '[INFO] CSV 原始数据：{n} 条'.format(n=len(df))
    )

    # 建表 + 清洗（含外键过滤）+ 插入
    import_events(df)


if __name__ == '__main__':
    main()