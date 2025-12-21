# Supabase 唯一访问者计数功能设置指南

## 1. 创建数据库表

在Supabase的SQL编辑器中运行以下SQL命令来创建所需的表：

```sql
-- 创建用于存储唯一访问者的表
CREATE TABLE IF NOT EXISTS unique_visitors (
    id SERIAL PRIMARY KEY,
    ip_hash TEXT NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 为ip_hash字段创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_unique_visitors_ip_hash ON unique_visitors(ip_hash);
```

## 2. 设置数据库权限

为了让前端应用能够访问数据库，需要设置适当的权限：

```sql
-- 授权对表的访问权限
GRANT SELECT ON unique_visitors TO anon, authenticated;
GRANT INSERT ON unique_visitors TO anon, authenticated;

-- 授权对序列的使用权限（用于自增ID）
GRANT USAGE ON SEQUENCE unique_visitors_id_seq TO anon, authenticated;
```

## 3. 可选：创建数据库函数（更安全的方式）

如果您希望使用数据库函数来处理逻辑，可以运行以下SQL：

```sql
-- 创建一个函数来安全地添加唯一访问者
CREATE OR REPLACE FUNCTION add_unique_visitor(p_ip_hash TEXT)
RETURNS TABLE(visitor_count BIGINT) AS $$
DECLARE
    visitor_exists INTEGER;
    total_count BIGINT;
BEGIN
    -- 检查IP哈希是否已存在
    SELECT COUNT(*) INTO visitor_exists
    FROM unique_visitors
    WHERE ip_hash = p_ip_hash;
    
    -- 如果不存在则插入
    IF visitor_exists = 0 THEN
        INSERT INTO unique_visitors(ip_hash) VALUES (p_ip_hash);
    END IF;
    
    -- 返回总访问者数量
    SELECT COUNT(*) INTO total_count FROM unique_visitors;
    RETURN QUERY SELECT total_count;
END;
$$ LANGUAGE plpgsql;
```

## 4. 前端配置

确保您的环境变量配置正确：

```env
VITE_SUPABASE_URL=your_supabase_project_url
VITE_SUPABASE_ANON_KEY=your_supabase_anon_key
```

## 5. 功能说明

- 普通访问计数：每次页面访问都会增加
- 唯一访问计数：通过IP哈希识别，相同IP只计算一次
- 点击"你是飘过此处的第X缕灵魂"文字后，会显示"你是路过此处的第X个唯一的你"
- 使用SHA-256哈希算法处理IP地址，保护用户隐私
- 使用localStorage缓存机制避免重复请求

## 6. 隐私说明

- 真实IP地址不会存储在数据库中
- 只存储IP地址的哈希值，无法反向解析出真实IP
- 符合隐私保护要求