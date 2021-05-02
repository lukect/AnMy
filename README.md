AnMy
====

Requirements
------------

* Java 15+
* MongoDB@localhost, default port.
* Twitter API auth in [`twitter4j.properties`](https://twitter4j.org/en/configuration.html)

Start
-----
```java -server -jar AnMy.jar <twitter_username> <page size (max 200)> [seconds delay between pages] [a=scan all pages]```