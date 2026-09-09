# Chat e Sprite — teste comparativo de 9 de setembro de 2026

## Resultado

As mesmas cinco perguntas passaram pelos controlos reais de Chat e pela bolha flutuante, com LFM2.5-350M Q4_K_M no emulador Android API 35. As dez respostas finais foram produzidas pelo modelo local. Guardar e pesquisar tiveram chamadas reais, e guardar exigiu aprovação. Isto é uma avaliação estreita e reproduzível, não uma certificação geral do modelo ou de todos os telemóveis.

[Transcrição completa, saídas cruas e eventos](../evidence/2026-09-09-paired/results.txt) · [Runner: 4 testes, 83,734 s](../evidence/2026-09-09-paired/instrumentation.txt)

## Método e resultados

Chat: sessão nova `286c6938-9bfa-47c2-b087-4de46239f472`. Sprite: sessão nova `a3ae99f5-c994-4be9-a5c1-ac775cc2d998`. Cada grupo manteve o seu histórico entre as cinco perguntas, sem histórico partilhado entre grupos. As notas de teste ficaram limitadas à respetiva sessão. Não foram usadas chaves cloud ou respostas simuladas nestas dez interações.

| Pergunta exata | Resultado nas duas interfaces | Chat / Sprite |
|---|---|---|
| Olá, Gents, como é que estás hoje? | Cumprimento PT, sem ferramenta | 2,281 / 2,576 s |
| Hello, Paladino! How are you today? | Cumprimento EN, sem ferramenta | 1,520 / 1,633 s |
| Resume numa frase: A reunião começa às dez e termina às onze. | Resposta correta numa frase, sem ferramenta | 2,310 / 2,716 s |
| Podes registar numa nota que o meu código de teste é safira 742? Depois explica em português o que guardaste. | notes_save, aprovação, escrita real e resposta PT | 5,470 / 10,380 s |
| Consulta as minhas notas com a ferramenta de pesquisa e diz-me qual é o código de teste que ficou guardado. | Uma correção limitada, notes_search real, resultado safira 742 e resposta PT | 9,013 / 8,668 s |

São amostras individuais, não percentis de desempenho. O tempo de guardar inclui o percurso de aprovação; no Sprite passa pela app e regressa à bolha. O teste confirma cinco mensagens de utilizador em cada sessão. O histórico completo fica na app, enquanto a bolha mostra apenas a interação atual.

## Problemas encontrados e corrigidos

O baseline respondeu em português ao cumprimento inglês. Separar o pequeno prompt de cumprimento por idioma corrigiu este caso; a localização ampla continua por avaliar.

Na quinta pergunta o modelo inicialmente afirmou que tinha pesquisado, mas tinha apenas recuperado o código do histórico. Alterar só o prompt não resolveu. O fluxo agora exige um resultado novo de notes_search quando deteta um pedido explícito de pesquisa de notas. Se faltar, pede uma correção dentro dos limites de turnos/tentativas; se continuar a faltar, termina em erro, sem mostrar uma pesquisa fictícia como concluída. Esta deteção é conservadora e não cobre todas as paráfrases ou ferramentas.

Na correção, o modelo produziu `notes_search("safira 742")`. O parser aceita agora essa forma restrita, além de JSON e da lista Pythonic com marcadores da Liquid. Apenas duas funções conhecidas e um argumento textual são aceites; não há avaliação de código. Testes rejeitam expressões, argumentos extra e chamadas concatenadas. Na execução final, a tentativa inicial sem ferramenta não foi apresentada ao utilizador; a chamada real foi executada antes da resposta final.

## Revisão visual e melhorias para implementar

[Bolha final sobre o launcher](../evidence/2026-09-09-paired/paired-sprite-5.png) · [Resposta na app](../evidence/2026-09-09-paired/paired-app-5.png) · [FAB e menu](../evidence/2026-09-09-paired/sprite-fab-menu.png)

| Prioridade | Observação | Alteração proposta e critério de aceitação |
|---|---|---|
| P1 | A bolha mostra asteriscos Markdown e a resposta repete a mesma informação | Apresentação compacta legível e instrução de resposta curta. Testar Markdown incompleto, acentos e texto longo sem perder o original na sessão |
| P1 | Guardar exige abrir a app, aprovar e regressar manualmente | Mostrar “Abrir para aprovar”, preservar sessão e rascunho, e facilitar o regresso à mesma bolha. Nunca aprovar automaticamente |
| P1 | “Open app” não explica que permite ler a resposta completa | Ação “Ver conversa completa” e indicação clara quando o texto é truncado; abrir a sessão correta |
| P1 | Estado Idle não informa se uma ferramenta foi realmente usada | Pequeno estado baseado nos eventos reais de execução, sem confiar no texto do modelo; não ocupar a bolha com logs |
| P2 | App, menus e bolha usam estilos/cores diferentes | Unificar cores, raios e estados de foco; preservar a iconografia própria navy/gold |
| P2 | “Ask Paladino” e “Open conversation” são pouco distintos; rótulos em inglês | Separar “Nova mensagem” e “Ver conversa”, localizar PT/EN e testar TalkBack e fontes grandes |

Estas melhorias estão anotadas, não implementadas nesta publicação. O Sprite já tem idle/thinking, arrasto, encaixe, nova conversa e acesso à app. Os testes anteriores de arrasto e ciclo do serviço não foram repetidos sem alterações nesses componentes. Permanecem por testar teclado/rotação, permissões revogadas, bateria e sobreposição em telemóvel físico. Não se promete presença sobre superfícies protegidas do Android.

## Validação adicional e limites

35 testes JVM passaram, lint e compilação debug/release passaram. A instrumentação final também verificou o ciclo de ferramenta anterior, isolamento/cifra de chaves sintéticas e seleção de fornecedores/menu FAB. Os testes de fornecedor não são inferência cloud real. API keys OpenAI/Z.ai estão ligadas ao transporte, mas OAuth e validação com contas reais continuam pendentes: [estado da autenticação](2026-09-08-provider-authentication.md).
