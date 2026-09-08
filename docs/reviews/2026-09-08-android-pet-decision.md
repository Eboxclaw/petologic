# Paladino: decisão sobre Sprite, bolha e widget Android

Revisão de 8 de setembro de 2026. Público: produto e implementação Android. Âmbito: Paladino, Tiny/Maxx, assets fornecidos e publicação do estado atual. Não inclui novos pets, serviços de acessibilidade, captura de ecrã, nem treino de pesos.

## Decisão

Manter uma janela nativa `TYPE_APPLICATION_OVERLAY` pequena e transparente como superfície principal do companheiro. Tocar abre a conversa compacta; arrastar move e encaixa na margem. O Chat conserva o histórico e a gestão de sessões. Widget do launcher e notificação complementam esta experiência. Esta é uma recomendação de arquitetura baseada nos requisitos e nas APIs, não uma garantia de funcionamento sobre qualquer ecrã.

| Opção | Adequação ao objetivo | Decisão |
|---|---|---|
| Overlay | Permite desenhar o personagem e controlos próprios sobre Activities, com autorização especial | Superfície principal |
| App widget | Vive no host/launcher, com dimensão e posicionamento geridos por este | Atalho opcional para o Chat |
| Notification bubble | Conversa numa Activity embutida; elegibilidade e apresentação controladas pelo sistema/utilizador | Alternativa futura, não requisito v1 |
| Picture-in-picture | Janela de Activity com interação e apresentação próprias do sistema, orientada sobretudo para consumo de vídeo | Não usar como base do personagem |
| Notificação | Estado e controlo para parar/abrir | Complemento, não substitui o boneco |

Fontes primárias: [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams), [App widgets overview](https://developer.android.com/develop/ui/views/appwidgets/overview), [Notification bubbles](https://developer.android.com/develop/ui/compose/notifications/bubbles), [Picture-in-picture](https://developer.android.com/develop/ui/views/picture-in-picture). Consultadas em 8 de setembro de 2026; documentação contínua.

## O que aprendemos com os Pets no computador

A captura fornecida mostra um personagem isolado junto à margem, com a conversa principal noutra janela. Na documentação oficial, o Pet acompanha atividade; a aparência não altera a execução. Há estados Running, Needs input, Ready e Blocked, posição persistida, acesso às conversas e respeito por movimento reduzido. A notificação é uma superfície separada. Adaptamos estes princípios ao toque: alvo de pelo menos 48 dp, margem acessível, bolha curta e ações explícitas. Não copiamos o formato de spritesheet web para dentro do runtime Android. [Pets — ChatGPT Learn](https://learn.chatgpt.com/docs/pets?surface=app).

A observação ao vivo da app Codex foi bloqueada pela ferramenta de Computer Use. A comparação visual baseia-se na captura enviada, e o comportamento descrito na documentação. A fotografia do telemóvel com pegas de redimensionamento é compatível com edição de widget no launcher; a imagem isolada não prova que seja PiP ou que permaneça sobre outras apps.

OpenPets separa pacote visual, estados e mapeamento de reações do host que valida e apresenta o personagem. Aplicamos essa separação com `PetReaction` e o catálogo, sem executar código vindo de assets. [OpenPets — Pet format](https://docs.openpets.dev/pet-format/).

## Restrições Android que interessam à implementação

- Overlays ficam abaixo de janelas críticas do sistema, como teclado e barra de estado. Não prometer literalmente “por cima de tudo”. Manter a janela limitada ao sprite/painel, evitando uma superfície transparente de ecrã inteiro que intercepte toques. [WindowManager](https://developer.android.com/reference/android/view/WindowManager.LayoutParams).
- Apps sensíveis podem impedir overlays com `HIDE_OVERLAY_WINDOWS`. Respeitar esse bloqueio. [Secure sensitive activities](https://developer.android.com/security/fraud-prevention/activities), atualizado em 6 de março de 2026.
- `specialUse` requer declaração e explicação do caso de uso; a submissão na Play Console está sujeita a revisão. O manifesto atual contém ambas. Isto não equivale a aprovação da loja. [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use).
- Em Android 15+, a exceção de arranque de FGS em background baseada em `SYSTEM_ALERT_WINDOW` exige overlay já visível. Iniciar pela app visível continua a ser o percurso escolhido. Não acrescentar um arranque automático no boot sem rever o lifecycle. [Android 15 behaviour changes](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-overlay).
- `POST_NOTIFICATIONS` não é pré-requisito para iniciar um FGS. A notificação continua a ser criada; quando recusada, o sistema mostra a indicação de serviço no gestor de tarefas, não na gaveta. Corrigido o bloqueio da nossa UI ao recusar esta permissão. [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
- Widgets usam RemoteViews e um conjunto limitado de Views. Não é correto afirmar que qualquer animação num widget é impossível: existem flippers. Isso também não transforma o widget numa janela que acompanha outras apps. Manter o widget estático nesta versão. [RemoteViews](https://developer.android.com/reference/android/widget/RemoteViews).

## Assets: escolha e contrato de entrega

Verificação dos ficheiros originais, sem reexportação ou alteração de pixels:

| Ficheiro | Bytes | Frames / duração | Observação |
|---|---:|---|---|
| paladino_idle.gif | 175312 | 8 / 6000 ms | Mantido como idle; menor que o WebP recebido |
| paladino_idle.webp | 437082 | 8 / 6000 ms | Alfa no canto; a pré-visualização fornecida mostra blocos castanhos a rever |
| paladino_thinking_10f_6000ms.webp | 183906 | 10 / 6000 ms | Selecionado para thinking; menor que GIF, conserva alfa parcial |
| paladino_thinking_10f_6000ms.gif | 217683 | 10 / 6000 ms | Frames 1–9 completamente opacos após composição pelo decoder de inspeção |

Os quatro ficheiros têm canvas 256 × 256. O canto (0,0) é preto opaco nos GIFs e no WebP thinking; o WebP idle tem alfa zero nesse ponto. Logo, “tem canal alfa” não significa “fundo totalmente removido”. Não declarei os assets como limpos. O fundo da janela é transparente, mas pixels opacos do ficheiro continuam visíveis.

**Formato preferido para próximas entregas: WebP animado lossless, RGBA com alfa real em todos os frames**, canvas fixo, posição/escala constantes, duração por frame e loop definidos. Um ficheiro por ciclo (`idle.webp`, `thinking.webp`) e PNG estático para fallback. Verificar cada frame sobre fundo claro, escuro e quadriculado; cantos e área exterior devem ter alfa zero. Evitar matte preto, rasto entre frames e blocos de compressão. Guardar os PNGs originais por frame como fonte de edição. A compressão final só é aceite depois dessa verificação, não apenas por ser WebP.

Android suporta GIF e WebP animados através de `ImageDecoder`/`AnimatedImageDrawable`, já usados nesta app. Não precisamos de WebView ou motor de jogo. [AnimatedImageDrawable](https://developer.android.com/reference/android/graphics/drawable/AnimatedImageDrawable).

## Estados e conversa

`RUNNING → thinking`; `IDLE → idle`. Aprovação e erro mantêm rótulos próprios e fallback idle enquanto não há ciclos adequados. O serviço troca apenas o drawable ao mudar de estado; não recria a conversa ou o modelo. A app e o cabeçalho da bolha usam o mesmo mapeamento. A preferência de animação e movimento reduzido mantêm fallback estático.

O agente responde em português ou inglês conforme o pedido. Nomes como `notes_save` e `notes_search` são identificadores estáveis. Os valores dos argumentos podem conter português, acentos e qualquer texto permitido pelo schema. JSON é um formato, não um idioma. A Liquid documenta chamadas Pythonic entre marcadores especiais por defeito, além de variantes estruturadas; o parser atual suporta o subconjunto validado, sem executar Python. O resultado real regressa como mensagem `tool`, seguido de nova geração de texto. [Liquid AI — Tool Use](https://docs.liquid.ai/lfm/key-concepts/tool-use).

## Osaurus e Insilico: aplicação correta das referências

Osaurus é uma app nativa para Mac com modelos locais e fornecedores cloud opcionais. É uma referência útil para separar agente, ferramentas, memória e fornecedor; não fornece um renderer Android nem demonstra que todos os seus modelos sejam Liquid. Tiny/Maxx deve manter identidade e sessão, com contexto explicitamente autorizado para cloud. [Osaurus](https://osaurus.ai/), [guia MCP](https://osaurus.ai/guides/mcp-setup).

O comunicado fornecido, de 3 de março de 2026, identifica **LFM2-2.6B-MMAI**, especializado em investigação farmacêutica. Mostra a direção de especialização de modelos eficientes, mas não é um benchmark de LFM2.5-350M nem prova dos nossos loops de ferramentas. As alegações de desempenho são das empresas; não foram reproduzidas aqui. [Comunicado Insilico Medicine / Liquid AI](https://www.prnewswire.com/news-releases/insilico-medicine-and-liquid-ai-announce-strategic-partnership-delivering-lightweight-scientific-foundation-models-for-drug-discovery-302702564.html).

## Critérios para considerar a experiência pronta

1. Em telemóvel físico: 20 alternâncias entre launcher e apps, 20 abrir/recolher, arrasto nos quatro limites, teclado e rotação sem crash ou controlos inacessíveis.
2. Animação: idle → thinking → idle após sucesso/cancelamento; aprovação e erro não ficam eternamente em thinking. Sem animação com ecrã desligado. Repetir com movimento reduzido.
3. Sessões: criar pela bolha, enviar, abrir a app e encontrar exatamente as mesmas mensagens; sem duplicação. Testar mudanças de sessão durante execução.
4. Permissões: recusar notificações, recusar/revogar overlay, parar pelo sistema e pela app; não reaparecer contra a decisão do utilizador.
5. Modelo: sessões novas PT e EN, pedidos sem ferramentas e guardar/pesquisar com aprovação; guardar transcrições, chamadas/resultados e contagem de falhas. Não substituir esta avaliação por respostas mock.
6. Recursos: medir 30 minutos com sprite parado, animado e oculto, no mesmo aparelho/brilho/carga. Publicar diferenças de CPU, memória e energia; não inventar p95 ou autonomia com base no emulador.
7. Distribuição: APK assinado, checksum, instalação limpa e atualização preservando sessões; link do site verificado. Vercel serve o site/link, não executa a app Kotlin Android.

## Lacunas e próximo sprint

Prioridade imediata: exportações com transparência limpa; validação física de overlay/teclado/TalkBack; testes de sessão e lifecycle; APK instalável pelo site. Depois: avaliação bilingue mais ampla, Maxx com credenciais reais e revisão de contexto, configuração individual de widgets, estado Ready/unread e gestão de memória do modelo quando só o sprite está visível.

A infraestrutura AppSearch/embedder existe; grafo Room, routing semântico e resumos duráveis precisam de avaliação de completude, não de ser anunciados como concluídos. Não foi feito treino de pesos. Novos pets e capacidades de rede permanecem fora do lançamento.

A pesquisa terminou quando a escolha de API e as limitações relevantes tinham suporte primário. Persistem três lacunas explícitas: comportamento OEM em hardware físico, aprovação Play e limpeza visual dos assets. Nenhuma destas é resolvida por consultar mais exemplos de desktop.
