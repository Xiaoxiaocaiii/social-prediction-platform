@echo off
cd /d "%~dp0"
echo Starting social-prediction-platform on http://localhost:8080 ...
mvn -s mvn-settings.xml spring-boot:run
