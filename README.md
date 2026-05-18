# MobContracts

![Paper](https://img.shields.io/badge/Paper-1.21.11-22c55e?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21-f97316?style=for-the-badge&logo=openjdk)
![Version](https://img.shields.io/badge/version-1.0.2-111827?style=for-the-badge)
![License](https://img.shields.io/badge/license-MIT-2563eb?style=for-the-badge)

Контракты на убийство мобов с наградой за выполнение.

## Версия

MobContracts 1.0.1

Paper 1.21.11  
API 1.21.11-R0.1-SNAPSHOT  
Java 21

## Команды

`/contracts list` - список целей
`/contracts take <mob>` - взять контракт
`/contracts status` - прогресс
`/contracts cancel` - отменить контракт

## Permission

`mobcontracts.use`
`mobcontracts.reload`
Reload по умолчанию доступен op.

## Функции

- выдаёт контракт на выбранного моба;
- считает убийства;
- после выполнения даёт опыт и изумруды;
- сохраняет активный контракт после рестарта.

## Сборка

```bash
./gradlew build
```

Готовый `.jar` будет в `build/libs/`.
