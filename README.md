# **«Поисковый движок»**
## Описание ##
:books: $\color[RGB]{60,200,120} {Используемые} \ {технологии}$: Java 21, Spring Boot 2.7.1, Maven, MySQL, Hibernate, Morphology Library.

Приложение с веб-интерфейсом в многопоточном режиме проводит индексацию страниц сайтов и сохраняет информацию в базу данных.
Далее по проиндексированным страницам проводится поиск (по запросу) и выдаются релевантные результаты.

## API ##
- **GET /**
  
  Открывает удобный веб-интерфейс для работы с приложением
  ![image](https://github.com/SStaro/searchengine/assets/102288630/27b9dd9b-ee14-4d2c-a54d-f55253666fc7)

- **GET /api/statistics**

  Выводит статистику, связанную с индексацией
  ![image](https://github.com/SStaro/searchengine/assets/102288630/1764ee3b-c80e-4ad4-8aa5-8f1a6e5adaa1)

- **GET /api/startIndexing**
  
  Начинает индексацию сайтов, указанных в файле *application.yaml*
  ![Видео без названия — сделано в Clipchamp (6)](https://github.com/SStaro/searchengine/assets/102288630/5a7c8e0d-9ae9-4f5f-ba81-75fea15fff9e)

- **GET /api/stopIndexing**
  
  Останавливает индексацию    
  ![Видео без названия — сделано в Clipchamp (5)](https://github.com/SStaro/searchengine/assets/102288630/2754c97c-17d3-496a-ad88-6a6e02427e90)

- **POST /api/indexPage**
  
  Запускает индексацию/переиндексацию страницы
  ![image](https://github.com/SStaro/searchengine/assets/102288630/913fef4f-a6c6-4435-b8ca-bcdc5c64a977)


- **GET /api/search**
  
  Проводит поиск по проиндексированным страницам
  ![image](https://github.com/SStaro/searchengine/assets/102288630/b5f4f987-ce0c-48e9-98a3-fc306043ff13)


## Инструкция по запуску ##
Чтобы скачать проект, нужно скачать заархивированную папку с проектом или клонировать репозиторий с помощью следующей команды:

`
git clone https://github.com/SStaro/searchengine.git <путь-до-папки-куда-клонируем-репозиторий>
`
> [!WARNING]
> Для использования этой команды должен быть установлен git.
Можно запустить приложение прямо из IDE либо через jar файл.


> [!IMPORTANT]
> Необходимо указать в файле *application.yaml*:
> - Информацию о подключении к базе данных: **логин**, **пароль**, **название**
> - Для избежания ошибок (так как некоторые сайты содержать эмодзи) следует использовать **Charset: utf8mb4** при создании базы данных
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
Нужно создать jar файл с помощью команды `mvn package`, или с помощью интерфейса IDE

![image](https://github.com/SStaro/Searchengine/assets/102288630/3011ba2f-1c20-4195-ab1a-b6a6ec741eff)

Запускаем приложение с помощью команды `java -jar "путь-до-файла"`
