# 404NotPure
南京大学23届软工Ⅱ大作业

## 当前分支
lab4

## 环境变量配置

请到对应官网购买或注册使用相应服务，并配置环境变量

* 阿里云：请详细参考*back_end/src/main/resources/application.yml*中的配置

* 支付宝：请详细参考*back_end/src/main/resources/application.yml*中的配置

* 豆包：需要到官网申请ARK_API_KEY，将其配置至用户环境变量中

## 启动前依赖服务

* mysql
  * 数据库名称为tomato，请提前创建
  * 用户名称和密码可自行在back_end/src/main/resources/application.yml中修改

* natapp
  * 启动内网穿透，并相应配置关于支付宝的环境变量
  * 如需使用完整支付功能，请参考博客 https://blog.csdn.net/mnn12/article/details/136299334

* redis：
  * 需要提前配置好Redis服务端
  * 启动cmd，进入Redis安装目录，输入`redis-server.exe redis.windows.conf`启动服务
  * 参考教程 https://www.runoob.com/redis/redis-install.html

## 启动指南

* 前端
  * 进入前端目录，执行`npm install`安装依赖
  * 执行`vite`启动前端服务

* 后端
  * 进入后端目录，执行`mvn clean install`安装依赖
  * 直接运行src/main/java/com/example/tomatomall/TomatoMallApplication.java