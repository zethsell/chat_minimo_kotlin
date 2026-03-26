# Checklist — refactor chat (spec v2.0)

**Fonte da verdade:** documento **Chat — Refactor Spec v2.0** (`chat_refactor_spec_v2.docx`).  
**Extensões de engenharia** (mesmo peso que o spec): seção **“Decisões complementares”** no [plano Cursor](file:///Users/zeth/.cursor/plans/análise_chat_refactor_spec_23b35a3a.plan.md) — `canonical`, envelope do historico, Mongo raiz+fallback, locks.  
**Acompanhamento:** marque `[ ]` → `[x]` ao concluir cada item. Ajuste subtarefas se o código real divergir do spec, mas registre o desvio num comentário ou PR.

---

## Legenda das decisões (D-xx)

| ID | Resumo |
|----|--------|
| D-01 | Dual-emit `chatUpdate` (router otimista + API canônica) + dedup no cliente (5 s, `chatId` + `lastMessageMillis`) + **`canonical`** (API `true`, router `false`/omitido; cliente **sempre** aplica canônico) |
| D-02 | `unreadCount` sempre mapa completo no WS; cliente só substitui |
| D-03 | Migração `FECHADO` → `RESOLVIDO`; enum `ABERTO \| AGUARDANDO \| RESOLVIDO \| ARQUIVADO` |
| D-04 | Estender `POST /chats/historico` (sem path `/v2/`) |
| D-05 | Jobs `@Scheduled` na API: auto aguardando + auto arquivar |
| D-06 | Fila `chat.events.out` + consumer no message-server + DLQ |
| D-07 | `participants[]` no Mongo `[idCorreios, carteiroId]` |
| D-08 | Android: `WebSocketManager` singleton + SharedFlow |
| D-09 | Epoch millis (Long) em WS e campos novos Mongo; REST pode ISO na serialização |

---

## 0. Pré-requisitos e alinhamento

- [ ] Spec v2 acessível a todo o time (cópia versionada no repo, se desejado)
- [ ] RabbitMQ: fila `chat.events.out`, DLX/DLQ e bindings documentados ou declarados em código
- [ ] Convenção única `chatId` == `id` Mongo (string) para payloads WS e REST

---

## 1. message-server-api

### 1.1 Modelo Mongo `Chat` v2

- [ ] Campos novos no **raiz** do documento: `participants`, `unreadCount` (map), `lastMessage`, `lastMessageMillis`, `lastMessageSender`, `status` v2 operacional, `statusChangedAt`, `statusChangedBy` (e demais do spec)
- [ ] **Não** remover nem exigir migração imediata de `detalhes` — convive com raiz
- [ ] **Leitura:** preferir campo no raiz; se ausente, **fallback** para `detalhes` (ex. `idCorreios`, `carteiroId`, `codigosObjeto`)
- [ ] **Escrita:** atualizar agregados de lista/preview sempre no raiz (e manter `detalhes` como hoje para compat)
- [ ] Índices: conforme spec (ajustar paths se índice for sobre raiz vs `detalhes` — refletir onde os dados passam a viver)

### 1.2 Migração (boot)

- [ ] Lock **Mongo** coleção `_migrations`: uma instância insere/registra `{ _id: "chat_v2" }` antes de rodar; demais **skip**; ao fim `completedAt` (padrão acordado: sem Redis/ZK)
- [ ] `FECHADO` → `RESOLVIDO` no raiz (D-03)
- [ ] Preencher campos raiz (`participants`, etc.) a partir de `detalhes` / legados onde ausentes
- [ ] Default `status = ABERTO` no raiz onde ausente
- [ ] Idempotência: reexecução segura

### 1.3 REST

- [ ] `PATCH /chats/{id}`: ação `markAllRead` + publicar envelope em `chat.events.out` (`chatUpdate` com **`canonical: true`**)
- [ ] `PATCH /chats/{id}`: ação `changeStatus` + validação de transições + `409` se inválido
- [ ] Deprecar / mapear comportamento antigo `PATCH` só `FECHADO` para não quebrar clientes
- [ ] Estender **body** de `POST /chats/historico`: opcionais `status`, `page`, `size`
- [ ] **Retrocompat:** se **nem `page` nem `size`** vierem no body → resposta = **mesmo shape atual** (fluxo/array legado)
- [ ] Se **`page` ou `size`** presentes → resposta = **envelope** `{ content, totalElements, page, size }` (`if` explícito no controller antes de montar resposta)
- [ ] Serialização: millis nos campos novos; ISO onde o spec pedir na camada REST

### 1.4 Consumer `chat.in`

- [ ] Após persistir mensagem: `updateLastMessage` + `increment` atômico em `unreadCount.<receiver>` no **raiz**
- [ ] Garantir `participants` populado no raiz
- [ ] Publicar `chatUpdate` canônico via `chat.events.out` com **`canonical: true`** no payload WS

### 1.5 Jobs (D-05)

- [ ] `@EnableScheduling`
- [ ] **ShedLock** + provider **MongoDB** (coleção ex. `shedlock`); `@SchedulerLock` com `lockAtMostFor` coerente com o intervalo (ex. ~55s para job a cada 5 min)
- [ ] Job `ABERTO` → `AGUARDANDO` (timeout; tratar `lastMessageMillis` nulo)
- [ ] Job `RESOLVIDO` → `ARQUIVADO` (após N dias)
- [ ] Publicar `chatStatusChanged` após cada transição automática

### 1.6 Publicação Rabbit

- [ ] `publishChatEvent`: exchange/fila/routing alinhados ao consumer do message-server
- [ ] Testes manuais ou integração: API → fila → WS

---

## 2. message-server

### 2.1 Dependências e config

- [ ] Spring AMQP / listener factory; `default-requeue-rejected: false` (DLQ)
- [ ] Propriedade `app.rabbit.chat-events-out-queue` (default `chat.events.out`)

### 2.2 Emissão otimista (D-01)

- [ ] `emitOptimisticChatUpdate` após roteamento bem-sucedido + `RECEBIDA` (conforme spec)
- [ ] Participantes na otimista: alinhar com spec (`sender`/`receiver` vs `participants[]` do Mongo quando disponível)
- [ ] `unreadCount: {}`, `source: router`, **`canonical: false`** (ou omitir), millis resolvido de `timestamp` da mensagem

### 2.3 Consumer `chat.events.out` (D-06)

- [ ] `ChatEventsConsumer` com `@RabbitListener`
- [ ] Parse envelope; `emitIfConnected` para cada `participant`
- [ ] Erro → log + rethrow para retry/DLQ

### 2.4 Contratos WS

- [ ] Documentar internamente: tipos `chatUpdate`, `chatStatusChanged`, `messageStatus`, `PONG`, mensagem sem `type`
- [ ] Garantir heartbeat inalterado

---

## 3. Cliente Android — chat_minimo

- [ ] `ChatContracts.kt` (tipos v2, millis)
- [ ] `ChatApplication` + `WebSocketManager` singleton (D-08) + fila offline envio
- [ ] `chatUpdate`: se `canonical == true` → **sempre** aplicar; senão → dedup por `lastMessageMillis` + janela 5 s (v2)
- [ ] `ChatListViewModel` + coleta dos SharedFlows
- [ ] **Tela inicial = lista de conversas estilo WhatsApp** (`ChatListScreen` como entry ou após splash): não abrir direto na conversa única
- [ ] **Linha de chat (estilo WhatsApp):** avatar circular + título (peer / participante que não sou eu) + preview uma linha + horário à direita (`lastMessageMillis` formatado)
- [ ] **Não lidas:** badge numérico + preview em negrito quando `unreadCount > 0`
- [ ] Divisores/ripple entre itens; `TopAppBar` “Conversas”; **abas por status** (filtro) compatíveis com o layout
- [ ] Estado vazio + pull-to-refresh ou botão atualizar (opcional)
- [ ] Reordenar lista por `lastMessageMillis` desc ao receber `chatUpdate` / `chatStatusChanged`
- [ ] Toque na linha → `ChatScreen` com `chatId`; `LaunchedEffect`: `markAllRead` otimista + `PATCH` em background
- [ ] `ChatHistoryApi` / `historico` com paginação opcional quando API pronta

---

## 4. Cliente Flutter — chat_minimo_flutter

- [ ] `chat_models.dart` (status, `ChatSummary`, eventos WS, millis)
- [ ] `ChatListState` / `ChangeNotifier` ou equivalente
- [ ] Dispatcher no `WebSocketService`: `chatUpdate`, `chatStatusChanged` + dedup (D-01) + prioridade **canônica** (`canonical == true`)
- [ ] **Tela inicial = `ChatListPage` estilo WhatsApp** (`main.dart` aponta para lista, não só `ChatPage`)
- [ ] **Linha de chat:** `ListTile` ou row custom — leading avatar circular, título = peer, subtitle = preview, trailing = horário formatado
- [ ] **Não lidas:** `Badge` / círculo com contagem + texto preview em `FontWeight.w600` quando há unread
- [ ] `AppBar` “Conversas”; `TabBar` por status alinhado ao padrão Material
- [ ] Estado vazio + refresh (`RefreshIndicator` ou botão)
- [ ] Ordenação por `lastMessageMillis` desc nos eventos ao vivo
- [ ] `onTap` → navegação para `ChatPage(chatId)`; bulk-ack + PATCH ao abrir
- [ ] Fila offline no envio WS

---

## 5. Validação fim-a-fim

- [ ] Duas mensagens rápidas: UI lista e conversa corretas
- [ ] Router otimista + canônico **mesmo millis** e **ordem invertida** (canônico primeiro): lista/unread corretos graças a `canonical: true`
- [ ] `markAllRead`: unread coerente após evento canônico
- [ ] Mudança de status (manual + job): `chatStatusChanged` chega aos dois clientes demo
- [ ] message-audit-panel ou log: inspecionar payloads `chat.events.out` e WS
- [ ] Regressão: `POST /chats/historico` **sem** `page`/`size` → resposta **idêntica em shape** à atual
- [ ] UX: lista bate com referência **estilo WhatsApp** (hierarquia título/preview/hora/unread) nos dois apps

---

## 6. Backlog explícito fora do v2 (não bloqueia spec)

- [ ] Autenticação/autorização em `PATCH` (não confiar só em `userId` no body)
- [ ] Redis / pub-sub para múltiplas instâncias do message-server
- [ ] Push (FCM/APNs)
- [ ] Room / Drift (cache local P3)

---

*Última atualização: spec **v2.0** + decisões complementares (canonical, envelope condicional, Mongo raiz+fallback, `_migrations` + ShedLock).*
