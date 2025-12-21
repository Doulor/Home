-- 创建用于存储唯一访问者的表
CREATE TABLE IF NOT EXISTS unique_visitors (
    id SERIAL PRIMARY KEY,
    ip_hash TEXT NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 为ip_hash字段创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_unique_visitors_ip_hash ON unique_visitors(ip_hash);

-- 可选：创建一个函数来安全地添加唯一访问者
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

-- 创建策略以允许客户端读取数据（根据您的安全需求调整）
-- 注意：这会允许任何人读取数据，您可能需要根据实际需求调整权限
GRANT USAGE ON SCHEMA public TO anon;
GRANT USAGE ON SCHEMA public TO authenticated;

-- 授权对表的访问权限
GRANT SELECT ON unique_visitors TO anon, authenticated;
GRANT INSERT ON unique_visitors TO anon, authenticated;

-- 授权对序列的使用权限（用于自增ID）
GRANT USAGE ON SEQUENCE unique_visitors_id_seq TO anon, authenticated;