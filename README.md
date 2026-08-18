
```bash
mvn clean package
java -jar .\target\file-copy-test.jar
```

Собрать и запускать командой:
```bash
java -jar file-copy-test.jar <copy type> <source-directory> <target-root-directory>
```

например
```bash
java -jar file-copy-test.jar 1 /home/test_directory/experiment/small /media/dmitry/EFF7-FD11/small-test
```

Режимы:
1 - без force
2 - с force(true)
3 - паралельное (очень долгая)
