# Easy Excel Module

## 📋 Tech stack

![Java](https://img.shields.io/badge/JDK_17-%23ED8B00.svg?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.3.6-6DB33F?style=flat&logo=spring&logoColor=white)


- JDK 17 / Spring Boot 3.36 환경에서 제작되었습니다.

- Easy HTTP Logging Aspect는 Spring Boot 프로젝트에서 AOP를 기반으로, HTTP 요청/응답 로그를 자동으로 출력해주는 라이브러리입니다.
  라이브러리 사용자는 별도 설정 없이 기본 로그를 남기거나, 필요하면 커스텀 로그 핸들러를 구현할 수 있습니다.

## ✨ 주요 기능 및 사용방법

- @LoggableApi 어노테이션이 적용된 클래스, 메소드의 HTTP 요청을 추적 후 DTO로 반환합니다.
  + ex : 
  ```java
  HttpRequestLog{
      createdDateTime = 2025-08-29T15:56:35,
      requesterIp = "127.0.0.1",
      request = "GET /api?param=value, Params: {...}",
      response = "test"
  }

- @LoggableApi가 클래스와 메소드에 동시에 적용된 경우 메소드가 우선순위를 가집니다.
- false로 지정 시 로그 DTO를 반환하지 않습니다.
- 반환된 DTO 객체는 핸들러로 컨트롤 할 수 있습니다.
- Slf4j 로그로 DTO 객체를 toString형태로 출력하는 기본 핸들러가 포함되어 있습니다.
- 로그 DTO에 대한 추가 조치가 필요한 경우 핸들러를 상속받아 구현하세요.
- 요청자 IP는 요청 헤더에 담긴 X-Forwarded-For값에서 추출합니다. 
## ✨ Installation 방법

### - Gradle
```java
dependencies {
  implementation files('libs/easy-http-log-aspect-0.0.1.jar')
}

```
### - Maven
```java
<dependency>
    <groupId>com.ywluv</groupId>
    <artifactId>easy-http-log-aspect</artifactId>
    <version>0.0.1</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/easy-http-log-aspect-0.0.1.jar</systemPath>
</dependency>
```
## ✨ 사용 예시
```java
@SpringBootApplication
@Import(com.ywluv.easy_http_log_aspect.config.HttpLoggingAspectConfig.class)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

```java
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@LoggableApi
public class SalesController {

  @GetMapping
  @LoggableApi(false)
  public String test(){
    return "test";
  }
}
```