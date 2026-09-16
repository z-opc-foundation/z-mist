#!/bin/bash
set -e

echo "===================== 开始打包 ====================="

# 1. 构建前端
echo "1. 构建前端项目..."
cd z-mist-admin-frontend
npm install
npm run build
cd ..

# 2. 复制前端资源到admin模块的resources/static目录
echo "2. 复制前端资源到admin模块..."
rm -rf z-mist-admin/src/main/resources/static/*
mkdir -p z-mist-admin/src/main/resources/static
cp -r z-mist-admin-frontend/dist/* z-mist-admin/src/main/resources/static/

# 3. 打包后端
echo "3. 打包后端项目..."
mvn clean
mvn install -DskipTests=true

echo "===================== 打包完成 ====================="
echo "jar包位置：z-mist-admin/target/z-mist-admin-*.jar"
