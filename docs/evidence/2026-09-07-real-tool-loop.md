# LFM 350M: conversa e ferramentas — verificação de 2026-09-07

## Resultado

Confirmado no emulador Android API 35 ARM64, através da interface Chat da app: o LFM2.5-350M Q4_K_M gera respostas naturais sem ferramentas e executa um ciclo modelo → ferramenta → resultado → resposta natural. Não foram usadas respostas simuladas nem escalada para a cloud. Wi-Fi e dados móveis estavam desligados.

O modelo já estava descarregado. Recalculei o SHA-256 no armazenamento privado da app: `7e6f72643caafc9a68256686638c4d7916f2cec76d1df478d4c3ddcd95a6aed4`, correspondente ao catálogo. Tamanho confirmado: 229312224 bytes. Foi reutilizado, sem novo download desnecessário.

## Casos reais

| Pedido | Verificação |
|---|---|
| Olá, Gents, como é que estás hoje? | Inferência real, resposta em português, nenhuma chamada de ferramenta. |
| Bom dia, Paladino! Tudo bem? | Nova sessão, resposta natural diferente da pergunta, nenhuma ferramenta. |
| Podes registar numa nota que o meu código de teste é safira 742? Depois explica em português o que guardaste. | O próprio modelo escolheu notes_save. Não existia nota antes da confirmação; o teste confirmou na UI. A nota foi persistida e o modelo voltou a gerar a resposta após o resultado. |
| Consulta as minhas notas com a ferramenta de pesquisa e diz-me qual é o código de teste que ficou guardado. | O próprio modelo escolheu notes_search, pesquisou safira 742, recebeu esse conteúdo real e respondeu com o código correto. |

Os pedidos de guardar e pesquisar foram explicitamente verificados como Route.Generate: não passam pelos atalhos determinísticos de guardar/procurar notas. Os cumprimentos inequívocos usam contexto curto, sem ferramentas; é o modelo que escreve a resposta, não uma resposta fixa da app. Um cumprimento seguido de uma ação não perde as ferramentas.

Exemplo real de resposta simples: “Olá! Estou pronto para ajudar. Em qual área você precisa de assistência hoje?”

Exemplo real após pesquisa: “O código de teste guardado é **safira 742**.” A resposta continua em linguagem natural; o JSON/protocolo interno não é apresentado como resposta ao utilizador.

[Transcrição completa, sessões, chamadas, resultados e métricas](2026-09-07-real-tool-transcript.txt).

## Problemas encontrados e corrigidos

1. O adaptador esperava um JSON próprio; o LFM também produz chamadas no formato Pythonic documentado. Agora há definições JSON tipadas e um parser restrito aos dois nomes e a um único argumento string. Não se executa Python: expressões, funções arbitrárias, múltiplas chamadas e argumentos inválidos são rejeitados. Os marcadores de ferramenta são preservados no JNI.
2. Os resultados estavam achatados como texto de utilizador. As mensagens agora mantêm os papéis user/assistant/tool até ao template nativo.
3. O histórico era anexado depois do pedido atual, confundindo uma pesquisa com um pedido anterior de escrita. Agora preserva ordem e papéis, separado do pedido atual. O histórico Tiny continua excluído de Maxx.
4. A recuperação antecipada de memória fazia o modelo pesquisar o marcador UNTRUSTED MEMORY. Com ferramentas disponíveis, a recuperação é feita explicitamente pelo modelo através da ferramenta. A recuperação contextual continua disponível quando as ferramentas estão desativadas e a leitura de memória está permitida.
5. O identificador interno de uma nota era confundido com o código guardado. A ferramenta de pesquisa agora entrega o conteúdo; os IDs permanecem no armazenamento da app.
6. O contexto geral provocava invenções de refeições/sono em cumprimentos. Cumprimentos inequívocos recebem um contexto curto próprio. Testes incluem a frase original e uma variante, sem resposta fixa nem um segundo modelo.

A integração foi confrontada com a [documentação oficial de ferramentas da Liquid AI](https://docs.liquid.ai/lfm/key-concepts/tool-use) e o [template oficial do modelo](https://huggingface.co/LiquidAI/LFM2.5-350M/blob/main/chat_template.jinja). A seleção da ferramenta continua sujeita à allowlist, limites do ciclo e confirmação para escrita.

## Validação final

- 24 testes JVM passaram: 11 core + 13 app. Incluem parser malformado/arbitrário, limites, repetição de chamadas, permissões, ordem do histórico e classificação conservadora dos cumprimentos.
- 16 testes no dispositivo passaram em 36.797 s, com realModel=true e overlay=true. Incluem os quatro pedidos acima, conversa com follow-up, Sprite flutuante, memória, sessões e descodificação do GIF.
- APK e APK de testes compilados e instalados; lint sem erros, com avisos existentes.
- [Saída integral do runner](2026-09-07-tool-regression-tests.txt).

## Limites desta confirmação

É prova dos casos executados, não uma garantia de fiabilidade para todos os pedidos. A redação ainda pode ser pouco natural (por exemplo, o modelo escreveu “notícia” onde deveria escrever “nota”) e varia com o contexto. Os atalhos determinísticos de guardar/procurar mantêm as suas respostas determinísticas; estes testes provam especificamente o ciclo gerado pelo modelo. Faltam uma avaliação mais ampla em português, ações recusadas/canceladas em UI com modelo real e medições num telemóvel físico. O streaming de texto fica retido enquanto há ferramentas disponíveis para não mostrar chamadas ou alegações de sucesso antes da execução; a resposta final é apresentada após validação.

Alterações locais na branch de trabalho. Sem push para main ou publicação de uma versão de produção.
