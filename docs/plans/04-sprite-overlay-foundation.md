# Paladino — base do Sprite, bolha e widget

Estado verificado em 2026-09-07. Apenas Paladino e a animação idle fornecida pelo utilizador. Nenhum novo ciclo ou personagem foi criado.

## Superfícies e comportamento

| Superfície | Base atual |
|---|---|
| Chat da app | Sessões, histórico completo, Tiny/Maxx, aprovação de ações e chamadas locais reais. |
| Sprite sobre outras apps | GIF idle transparente; tocar abre a bolha; arrastar move e encaixa na margem; posição normalizada guardada. |
| Bolha | Painel azul-escuro/dourado com Sprite, estado e sessão, última resposta, compositor, Enviar/Parar, Nova conversa, Abrir app e Recolher. O título permite arrastar o painel. |
| FAB da app | Botão de 56 dp com ícone próprio de conversa/escudo e menu com ícones correspondentes. |
| Widget do launcher | Sprite estático, superfície arredondada e alvo de sessão configurável; inicia o Chat. O launcher gere a posição do widget. |

Os ícones vetoriais foram desenhados em código para este projeto: conversa/escudo, nova conversa/losango, enviar/ponta de lança, abrir app, recolher e parar. Não foram alterados os pixels dos assets fornecidos.

## Separação de responsabilidades

O overlay é uma projeção do SessionHub e do SessionController. Não contém um segundo modelo, histórico ou conjunto de permissões. As mensagens e respostas passam pelo mesmo armazenamento Room usado pela app. A bolha mostra apenas a resposta recente; o histórico integral fica no Chat. Aprovações de escrita/cloud continuam na app.

SpriteOverlayService gere a janela Android, gestos, posição e animação. OverlayPosition converte posições normalizadas em coordenadas limitadas à área disponível. A posição é restaurada após parar/iniciar o serviço e recalculada após mudanças de configuração. Mudanças de tamanho/animação nas preferências atualizam o overlay ativo.

É necessário ativar o Sprite e conceder a permissão Android para aparecer sobre outras apps. Há uma notificação com controlo para parar. O serviço não reinicia no arranque nem promete sobreviver a encerramento forçado pelo Android. O Sprite é escondido com o ecrã bloqueado/desligado; superfícies protegidas podem impedir overlays. Não lê o ecrã nem outras apps.

## Evidência

- 26 testes JVM passaram, incluindo limites de posição e restauração matemática.
- 16 testes Android passaram em 38.986 s. O teste de overlay arrasta o Sprite, verifica os limites, para/reinicia o serviço, confirma a posição restaurada, cria uma conversa e obtém uma resposta real do LFM sobre o launcher.
- App e APK de testes compilados; lint passou sem erros.
- [Bolha com resposta real](../evidence/2026-09-07-overlay-v2-reply.png), [idle flutuante](../evidence/2026-09-07-overlay-v2-idle.png), [saída dos testes](../evidence/2026-09-07-overlay-v2-tests.txt).

## Próximos passos

Validar num telemóvel físico: teclado aberto perto dos limites, rotação/cutouts, TalkBack, revogação de permissão e bateria. Confirmar colocação e abertura do widget num launcher real. Depois melhorar seleção de sessões no gestor e pequenos estados de feedback. Novas animações permanecem fora desta etapa.

Maxx tem integração BYOK/OpenRouter e revisão do contexto, mas ainda falta validação com um fornecedor real. APK de preview é produzido pelo workflow Android do GitHub; publicação pública no site e assinatura de release continuam pendentes.
