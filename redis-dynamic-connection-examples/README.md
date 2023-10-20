# redis-dynamic-connection-examples

## 背景
例如现在`project-a`通过spring-boot自动配置连接到了 `127.0.0.1:3306, db-0`。
但，此时`project-a`还期望能操作其中的任意database，或者再连接另外1个redis `127.0.0.2:3306, db-x`

### 只是切换 database
1) 需要保证 序列化/反序列化 正确！