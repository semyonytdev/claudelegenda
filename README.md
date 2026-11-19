# Claude Legenda (Fabric, Minecraft 1.21.11)

Маппинги: **Yarn 1.21.11+build.4**. Java 21. Fabric API 0.141.x.

## Сборка
1. Положите в папку проекта Gradle wrapper (проще всего: сгенерируйте шаблон на
   https://fabricmc.net/develop/template, выберите **Yarn**, и скопируйте в него папку `src`
   и файлы `build.gradle`, `gradle.properties`, `settings.gradle` из этого проекта).
2. `./gradlew build` -> `build/libs/claude-legenda-1.0.0.jar`.
3. Положите jar и Fabric API в `mods/` клиента (и сервера, если играете на сервере).

## Настройка
`ClaudeConfig.java`: координаты входа в базу (`ENTRANCE_X/Z`), радиус постройки, глубина.
База строится сама, когда игрок впервые подходит ближе `BUILD_RADIUS` к этим координатам.

## Структура
- `ClaudeLegenda` — регистрация всего (частица, пакет, события, команды)
- `StoryManager` — флаги игрока, эффекты, главный тик
- `BaseBuilder` — генерация базы, поршневая ловушка, лут
- `Books` — три книги, `ClaudeCommands` — `/claude stop`, `/claudehello`
- `client/ClaudeLegendaClient` — серый шейдер, звёзды, запрет бега
- `mixin/client/GameRendererAccessor` — доступ к `GameRenderer#setPostProcessor`
- ресурсы: `post_effect/gray.json`, `shaders/post/*.fsh`, `particles/red_star.json`, текстура звезды

## Известные риски (код не компилировался в среде разработки)
- Имена классов Yarn проверены по javadoc для `Entity`, `ServerPlayerEntity`, `ClientWorld`, `GameRenderer`,
  `ItemStack`; не проверены: `FlameParticle.Factory`, `SignBlockEntity.setText`,
  `WrittenBookContentComponent` (конструктор). Если компилятор ругнётся — поправьте по подсказке IDE.
- Формат post-effect JSON и uniform `InSampler` взяты по схеме 1.21.5+; при ошибке смотрите `latest.log`.
