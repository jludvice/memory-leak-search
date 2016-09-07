# Memory leak search

Goal of this tiny project is to automate searching for memory leaks in Apache Karaf.  
It searches for leaked references to classloaders.

## Build
Build maven project as usual
```shell
mvn clean package
```


## Usage

1. take heap dump with command
    ```shell
    jmap -dump:live,format=b,file=file_with_heapdump.hprof PID
    ```

1. pass dump to this program
    ```shell
    java -jar target/memory-leak-search-1.0-SNAPSHOT.jar /path/to/file_with_heapdump.hprof
    ```