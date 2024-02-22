# **Итоговый проект «Поисковый движок» Skillbox**
## Описание ##
:books: $\color[RGB]{155,255,200} {Используемые} \ {технологии}$: Java 21, Spring Boot 2.7.1, Maven, MySQL, Hibernate, Morphology Library.

Приложение в многопоточном режиме проводит индексацию страниц сайтов и сохраняет информацию в базу данных.
Далее по проиндексированным страницам проводится поиск (по запросу) и выдаются релевантные результаты.

## API ##
- **GET /**
  
  Открывает удобный веб-интерфейс для работы с приложением
- **GET /api/statistics**

  Выводит статистику, полученную после индексации
- **GET /api/startIndexing**
  
  Начинает индексацию сайтов, указанных в файле *application.yaml*
- **GET /api/storIndexing**
  
  Останавливает индексацию
- **POST /api/indexPage**
  
  Запускает индексацию/переиндексацию страницы
- **GET /api/search**
  
  Проводит поиск по проиндексированным страницам

> [!IMPORTANT]
> Необходимо указать в файле *application.yaml*:
> - Информацию о подключении к базе данных: **логин**, **пароль**, **название**
> * Информацию об индексируемых сайтах: **url**, **имя**, **домен**

 ```java
 server:
  port: 8080

spring:
  datasource:
    username: root
    password: root
    url: jdbc:mysql://localhost:3306/search_engine?useSSL=false&requireSSL=false&allowPublicKeyRetrieval=true

  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
    hibernate:
      ddl-auto: create
      show-sql: true

indexing-settings:
  sites:
    - url: https://www.playback.ru/
      name: PlayBack.Ru
      domain: playback.ru
    - url: https://metanit.com/
      name: metanit
      domain: metanit.com
```
