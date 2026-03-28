# Arquitetura — chat_minimo (Kotlin)

App demo **carteiro** contra o **BFF** (REST `/chat/...`, login `POST /v1/autenticacao`, SSE `/sse/stream`). Organização espelhando o espírito do projeto Flutter: camadas explícitas, contratos tipados e **sem `Map<String, Any?>` na UI ou no domínio**.

## Camadas

| Camada | Pacote (raiz `com.example.chat_minimo_kotlin`) | Responsabilidade |
|--------|-----------------------------------------------|------------------|
| **Domain** | `domain.model`, `domain.repository`, `domain.service`, `domain.realtime` | Entidades (`ChatSummary`, `ChatMessage`, …), interfaces de repositório, regras puras (merge de mensagens, preview da inbox), constantes do protocolo SSE/WS. |
| **Data** | `data.dto`, `data.mapper`, `data.remote` (+ `data.remote.api`), `data.repository`, `data.sse`, `data.queue`, `data.json` | DTOs Gson, mapeamento DTO → domínio, **Retrofit** (`BffChatApi`, `BffAuthApi`) sobre OkHttp, implementações de repositório, parser SSE → `ParsedRealtimeEvent`, fila outbound. |
| **Presentation** | `presentation.chat` | `ChatViewModel` (`@HiltViewModel`): `StateFlow` de lista e thread, transporte SSE, orquestração de casos de uso. |
| **Core** | `core.config`, `core.session` | `ChatAppConfig` (defaults de `strings.xml`), `AuthSessionHolder` (cookie + usuário + base BFF, persistido via `ChatTokenStore`). |
| **DI** | `di` | Hilt: `NetworkModule`, `CoroutineModule`, `RepositoryModule`, interceptador de cookie. |
| **UI** | `ui.pages`, `ui.components`, `ui.theme` | Compose: apenas modelos de domínio (`ChatSummary`, `ChatMessage`) e callbacks; sem parsing JSON. |

## Fluxo de dados

1. **Login**: `AuthRepository` → `BffAuthRemoteDataSource` → `BffAuthApi` (Retrofit + OkHttp **sem** cookie; URLs absolutas [@Url]) → `AuthSessionHolder.updateSession` → prefs.
2. **REST autenticado**: `ChatRepository` → `ChatRemoteDataSource` → `BffChatApi` (Retrofit + `@ChatHttpClient`, interceptor de `Cookie`).
3. **SSE**: `SseManager` lê cookie de `AuthSessionHolder`; cada linha `data:` passa por `RealtimeSseParser` → eventos tipados; o `ChatViewModel` aplica patches em `_chats` / `_messages`.
4. **Mensagens**: respostas JSON viram `ChatMessageDto` → `ChatMessageMapper` → `ChatMessage`. Incremental: `MessageMergeService.merge`.

## Documentação no código

- **KDoc** em tipos e funções públicas de domínio, repositórios e parsers.
- Comentários inline só onde a regra de negócio não é óbvia pelo nome (evitar ruído).

## Testes unitários

- `MessageMergeServiceTest`: merge/dedupe/timestamps.
- `ChatSseOutboundQueueTest`: ordem FIFO da fila outbound.
