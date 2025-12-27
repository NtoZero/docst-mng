# Troubleshooting Guide

이 디렉토리는 개발 중 발생한 문제와 해결 방법을 정리합니다.

## 디렉토리 구조

```
troubleshooting/
├── be/                    # Backend 관련 이슈
│   └── lazy-initialization-async.md
├── fe/                    # Frontend 관련 이슈
│   └── sync-polling-issue.md
└── README.md
```

## Frontend 이슈

| 문서 | 설명 |
|------|------|
| [sync-polling-issue.md](./fe/sync-polling-issue.md) | SSE와 Polling 동시 실행, Stale Closure 문제 |

## Backend 이슈

| 문서 | 설명 |
|------|------|
| [lazy-initialization-async.md](./be/lazy-initialization-async.md) | 비동기 스레드에서 LazyInitializationException 발생 |

## 공통 이슈

(추후 추가 예정)
