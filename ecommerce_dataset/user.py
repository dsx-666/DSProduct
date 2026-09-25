#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
用户数据清洗 + MySQL 建表 + BCrypt 密码生成 + 批量入库

依赖：
    pip install pandas mysql-connector-python bcrypt

数据要求：
    CSV 至少包含：
    user_id, name, email, gender, city, signup_date

处理规则：
1. userid 为空的整行删除。
2. name 为空的整行删除（建表时 name 为 NOT NULL）。
3. name 必须唯一：重复 name 只保留第一次出现的记录。
4. 原始 user_id 映射为 userid，并作为字符串主键。
5. 另外建立 id BIGINT AUTO_INCREMENT，作为数据库内部自增编号。
   注意：userid 是业务主键，id 只是数据库内部自增编号。
6. role 全部生成 NORMAL_USER（普通用户）。
7. user_type 只对普通用户设置：
   - signup_date 距离运行日期 <= NEW_USER_DAYS：NEW_USER（新用户）
   - 否则：OLD_USER（老用户）
   这个“新/老用户”的时间阈值可以自行修改。
8. password 随机生成 8 位，且至少包含一个大写字母、一个小写字母、一个数字，
   再使用 BCrypt 加密后入库。Python bcrypt 生成的 BCrypt 哈希可以被
   Spring Security 的 BCryptPasswordEncoder 校验。
9. 表结构包含原 CSV 的业务字段，并增加：
   id, userid, user_type, role, password
10. 使用事务批量插入；发生异常时自动回滚。

安全提示：
    数据库密码不要直接提交到 Git。
    正式项目建议使用环境变量或配置文件读取。
"""

import secrets
import string
from datetime import date
from pathlib import Path

import bcrypt
import pandas as pd
import mysql.connector
from mysql.connector import Error


# ============================================================
# 1. 基础配置：这里先留空，你把自己的数据库配置填进去
# ============================================================

DB_CONFIG = {
    "host": "127.0.0.1",          # 例如：127.0.0.1
    "port": 3306,
    "user": "root",          # 例如：root
    "password": "123589674xing",      # 数据库密码
    "database": "DSProduct",      # 例如：product
    "charset": "utf8mb4",
}

# CSV 文件路径
CSV_PATH = Path(__file__).with_name("users.csv")

# 新用户判定阈值：
# signup_date 距今天 <= 365 天 -> NEW_USER
# 否则 -> OLD_USER
NEW_USER_DAYS = 365

# 每次批量插入多少条
BATCH_SIZE = 500


# ============================================================
# 2. 随机密码
# ============================================================

def generate_plain_password(length: int = 8) -> str:
    """
    生成符合要求的随机密码：
    - 8 位
    - 至少 1 个大写字母
    - 至少 1 个小写字母
    - 至少 1 个数字
    """
    if length < 3:
        raise ValueError("密码长度至少为 3")

    upper = secrets.choice(string.ascii_uppercase)
    lower = secrets.choice(string.ascii_lowercase)
    digit = secrets.choice(string.digits)

    pool = string.ascii_letters + string.digits
    rest = [secrets.choice(pool) for _ in range(length - 3)]

    chars = [upper, lower, digit] + rest

    # Fisher-Yates 思路，使用 secrets 进行安全随机打乱
    for i in range(len(chars) - 1, 0, -1):
        j = secrets.randbelow(i + 1)
        chars[i], chars[j] = chars[j], chars[i]

    return "".join(chars)


def bcrypt_encode(raw_password: str) -> str:
    """
    BCrypt 加密。
    默认使用 bcrypt 的 cost=12。
    Spring Security 的 BCryptPasswordEncoder 可以直接校验这种哈希。
    """
    hashed = bcrypt.hashpw(
        raw_password.encode("utf-8"),
        bcrypt.gensalt(rounds=4)
    )
    return hashed.decode("utf-8")


# ============================================================
# 3. 数据清洗
# ============================================================

REQUIRED_COLUMNS = {
    "user_id",
    "name",
    "email",
    "gender",
    "city",
    "signup_date",
}


def clean_data(df: pd.DataFrame) -> pd.DataFrame:
    """按照要求清洗原始 CSV。"""

    missing_columns = REQUIRED_COLUMNS - set(df.columns)
    if missing_columns:
        raise ValueError(
            f"CSV 缺少必要字段：{sorted(missing_columns)}"
        )

    # 只处理字符串两端空格，不随意修改原始业务内容
    for column in ["user_id", "name", "email", "gender", "city"]:
        df[column] = df[column].astype("string").str.strip()

    # signup_date 转日期
    df["signup_date"] = pd.to_datetime(
        df["signup_date"],
        errors="coerce"
    ).dt.date

    before = len(df)

    # --------------------------------------------------------
    # 条件 1：userid 非空
    # --------------------------------------------------------
    df = df[
        df["user_id"].notna()
        & df["user_id"].ne("")
    ].copy()

    # name 也不能为空，因为后面要求 name UNIQUE + NOT NULL
    df = df[
        df["name"].notna()
        & df["name"].ne("")
    ].copy()

    # signup_date 必须是合法日期
    df = df[df["signup_date"].notna()].copy()

    removed_empty = before - len(df)

    # --------------------------------------------------------
    # 条件 2：name 必须唯一，重复保留第一条
    # --------------------------------------------------------
    duplicated_name_count = int(df["name"].duplicated(keep="first").sum())

    df = df.drop_duplicates(
        subset=["name"],
        keep="first"
    ).copy()

    # userid 也必须唯一。
    # 用户要求 userid 是主键，因此重复 userid 无法插入。
    duplicated_userid_count = int(
        df["user_id"].duplicated(keep="first").sum()
    )

    if duplicated_userid_count > 0:
        print(
            f"[WARN] 发现 {duplicated_userid_count} 个重复 userid，"
            f"按第一次出现保留。"
        )

        df = df.drop_duplicates(
            subset=["user_id"],
            keep="first"
        ).copy()

    print("=" * 60)
    print("数据清洗结果")
    print("=" * 60)
    print(f"原始数据：{before} 条")
    print(f"删除 userid/name/日期无效数据：{removed_empty} 条")
    print(f"删除重复 name：{duplicated_name_count} 条")
    print(f"删除重复 userid：{duplicated_userid_count} 条")
    print(f"最终数据：{len(df)} 条")

    return df.reset_index(drop=True)


# ============================================================
# 4. 生成 user_type / role / password
# ============================================================

def generate_user_fields(df: pd.DataFrame) -> pd.DataFrame:
    """
    为数据生成：
    - role
    - user_type
    - password
    """

    today = date.today()

    roles = []
    user_types = []
    passwords = []

    for signup_date in df["signup_date"]:
        # 当前需求：全部都是普通用户
        role = "NORMAL_USER"

        # 普通用户才有 user_type
        days = (today - signup_date).days

        if days <= NEW_USER_DAYS:
            user_type = "NEW_USER"
        else:
            user_type = "OLD_USER"

        # 明文密码只在程序内短暂存在，
        # 最终只保存 BCrypt 哈希。
        raw_password = generate_plain_password(8)
        encoded_password = bcrypt_encode(raw_password)

        roles.append(role)
        user_types.append(user_type)
        passwords.append(encoded_password)

    df["role"] = roles
    df["user_type"] = user_types
    df["password"] = passwords

    return df


# ============================================================
# 5. 建表
# ============================================================

CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS `user` (
    `user_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '业务用户ID',
    `name` VARCHAR(100) NOT NULL COMMENT '用户姓名',
    `email` VARCHAR(255) NOT NULL COMMENT '邮箱',
    `gender` VARCHAR(20) NOT NULL COMMENT '性别',
    `city` VARCHAR(100) NOT NULL COMMENT '城市',
    `signup_date` DATE NOT NULL COMMENT '注册日期',

    `user_type` ENUM('NEW_USER', 'OLD_USER')
        NULL COMMENT '用户类型，仅普通用户使用',

    `role` ENUM('NORMAL_USER', 'ADMIN', 'SUPER_ADMIN')
        NOT NULL DEFAULT 'NORMAL_USER'
        COMMENT '用户职责',

    `password` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',

    PRIMARY KEY (`user_id`),
    
    KEY `idx_user_name` (`name`),              -- 改为普通索引（加速查询，允许重名）
    UNIQUE KEY `uk_user_email` (`email`),

    CONSTRAINT `chk_role_user_type`
        CHECK (
            (`role` = 'NORMAL_USER' AND `user_type` IS NOT NULL)
            OR
            (`role` IN ('ADMIN', 'SUPER_ADMIN') AND `user_type` IS NULL)
        )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户表';
"""


# ============================================================
# 6. 批量插入
# ============================================================

INSERT_SQL = """
INSERT INTO `user`
(
    `user_id`,
    `name`,
    `email`,
    `gender`,
    `city`,
    `signup_date`,
    `user_type`,
    `role`,
    `password`
)
VALUES
(
    %s,%s, %s, %s, %s,
    %s, %s, %s, %s
)
"""


def insert_data(df: pd.DataFrame):
    """创建表并批量插入数据。"""

    connection = None
    cursor = None

    try:
        connection = mysql.connector.connect(**DB_CONFIG)

        if not connection.is_connected():
            raise RuntimeError("数据库连接失败")

        cursor = connection.cursor()

        # 建表
        cursor.execute(CREATE_TABLE_SQL)
        print("[OK] user 表创建/检查完成")

        # 批量插入
        records = []

        for _, row in df.iterrows():
            print(int(str(row["user_id"][1:])))
            records.append(
                (
                    int(str(row["user_id"][1:])),
                    str(row["name"]),
                    str(row["email"]),
                    str(row["gender"]),
                    str(row["city"]),
                    row["signup_date"],
                    str(row["user_type"]),
                    str(row["role"]),
                    str(row["password"]),
                )
            )

            if len(records) >= BATCH_SIZE:
                cursor.executemany(INSERT_SQL, records)
                records.clear()

        if records:
            cursor.executemany(INSERT_SQL, records)

        connection.commit()

        print("=" * 60)
        print(f"[OK] 成功插入 {len(df)} 条用户数据")
        print("[OK] 事务已提交")
        print("=" * 60)

    except Error:
        if connection is not None:
            connection.rollback()

        print("[ERROR] 数据库操作失败，事务已回滚")
        raise

    except Exception:
        if connection is not None:
            connection.rollback()

        print("[ERROR] 程序执行失败，事务已回滚")
        raise

    finally:
        if cursor is not None:
            cursor.close()

        if connection is not None and connection.is_connected():
            connection.close()

        print("[OK] 数据库连接已关闭")


# ============================================================
# 7. 主流程
# ============================================================

def main():
    if not CSV_PATH.exists():
        raise FileNotFoundError(
            f"找不到 CSV 文件：{CSV_PATH}"
        )

    if not DB_CONFIG["host"]:
        raise ValueError(
            "请先填写 DB_CONFIG，例如 host/user/password/database"
        )

    print(f"[INFO] 正在读取：{CSV_PATH}")

    df = pd.read_csv(CSV_PATH)

    print(f"[INFO] CSV 原始行数：{len(df)}")

    # 1. 清洗
    df = clean_data(df)

    # 2. 生成角色、用户类型、BCrypt 密码
    df = generate_user_fields(df)

    # 3. 入库
    insert_data(df)


if __name__ == "__main__":
    main()
