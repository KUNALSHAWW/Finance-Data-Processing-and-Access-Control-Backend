@echo off
REM Script to reset git history and make you the sole contributor
cd /d "c:\Users\kunal\OneDrive\Desktop\Assignments\zovryn\Finance-Data-Processing-and-Access-Control-Backend"

echo.
echo ========================================
echo Starting fresh git history...
echo ========================================
echo.

REM Remove old git directory
rmdir /s /q .git

REM Initialize fresh repo
git init

REM Configure your identity
git config user.name "Kunal Kumar Shaw"
git config user.email "kunalshawkol16@gmail.com"

REM Delete temporary script files before commit
del /q move_files.js 2>nul
del /q move_files.bat 2>nul

REM Add all files
git add -A

REM Create initial commit
git commit -m "Initial commit: Finance Data Processing and Access Control Backend

- Spring Boot 3.5 with Java 21
- JWT authentication with role-based access control
- Financial records CRUD with soft delete
- Dashboard analytics API
- Swagger/OpenAPI documentation"

REM Add remote
git remote add origin https://github.com/KUNALSHAWW/Finance-Data-Processing-and-Access-Control-Backend.git

REM Rename branch to main
git branch -M main

REM Force push to overwrite remote history
git push --force -u origin main

REM Self-delete this script
echo.
echo ========================================
echo Done! You are now the sole contributor.
echo Cleaning up...
echo ========================================
echo.

REM Delete this script
(goto) 2>nul & del "%~f0"
