#!/bin/bash

mysqldump -d -h127.0.0.1 -uroot -pmy-secret-pw --add-drop-table keel > keel.sql
mysql -h127.0.0.1 -uroot -pmy-secret-pw keel < keel.sql
