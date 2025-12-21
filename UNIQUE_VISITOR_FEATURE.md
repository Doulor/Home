# 时光胶囊唯一访问者计数功能实现总结

## 功能概述

实现了时光胶囊中按IP统计的唯一访问者计数功能，具体包括：

1. **默认显示**：你是飘过此处的第 [计数] 缕灵魂（总访问量）
2. **点击切换**：点击文字后显示：你是路过此处的第 [计数] 个唯一的你（唯一访问者数量）
3. **IP去重**：使用IP哈希算法确保同一IP只计算一次
4. **隐私保护**：真实IP不存储，仅存储哈希值

## 技术实现

### 前端实现（MoreContent.vue）

1. **新增响应式数据**：
   - `uniqueVisitorCount`：存储唯一访问者计数
   - `showUniqueCounter`：控制显示哪种计数

2. **新增计算属性**：
   - `formattedUniqueCount`：格式化唯一访问者计数

3. **新增方法**：
   - `getUniqueVisitorCount()`：获取唯一访问者总数
   - `incrementUniqueVisitorCount()`：增加唯一访问者计数
   - `toggleCounterDisplay()`：切换计数显示

4. **优化逻辑**：
   - 使用localStorage缓存IP哈希，避免重复请求
   - 处理并发插入情况
   - 错误处理和异常捕获

### 后端实现（Supabase）

1. **数据库表**：
   ```sql
   CREATE TABLE unique_visitors (
       id SERIAL PRIMARY KEY,
       ip_hash TEXT NOT NULL UNIQUE,
       created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
   );
   ```

2. **数据库函数**（可选）：
   ```sql
   CREATE OR REPLACE FUNCTION add_unique_visitor(p_ip_hash TEXT)
   RETURNS TABLE(visitor_count BIGINT)
   ```

## 使用说明

1. **首次访问**：显示总访问量
2. **点击文字**：切换显示唯一访问者数量
3. **同一IP重复访问**：唯一访问者数量不会增加
4. **不同IP访问**：唯一访问者数量会增加

## 隐私保护

- 使用SHA-256算法对IP地址进行哈希处理
- 数据库中不存储真实IP地址
- 符合隐私保护法规要求

## 部署步骤

1. 在Supabase项目中创建`unique_visitors`表
2. 设置适当的数据库权限
3. 部署更新后的前端代码
4. 测试功能是否正常工作

## 注意事项

- 需要用户同意隐私政策（如适用）
- 考虑代理、NAT等可能导致多个用户共享IP的情况
- 定期清理过期数据（可选）