#!/bin/sh
sudo -u postgres psql -c "create user tester createdb password 'testpassword';"
sudo -u postgres psql -c "create database testdb owner tester;"
exit 0