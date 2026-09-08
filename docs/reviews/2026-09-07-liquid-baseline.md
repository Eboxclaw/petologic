# Baseline Liquid AI antes da afinação

Verificado em 2026-09-07. Manter o LFM2.5-350M obrigatório, sem fine-tuning ou novos checkpoints nesta etapa.

## Padrão primário

A [model card oficial do 350M](https://huggingface.co/LiquidAI/LFM2.5-350M) recomenda temperature 0.1, top_k 50 e repetition_penalty 1.05. São os valores atuais. A app limita o contexto a 4096 e a saída a 512 por defeito como orçamento móvel; o top_p 0.9 é uma opção da app, não uma recomendação atribuída à model card.

O [protocolo oficial de ferramentas](https://docs.liquid.ai/lfm/key-concepts/tool-use) usa definições JSON no system prompt, chamadas delimitadas por tool_call_start/tool_call_end, resultado no papel tool e nova geração da resposta final. Pythonic é o formato nativo; JSON é uma alternativa documentada. O adaptador atual aceita ambas as representações restritas, sem executar código Python, e o flow mantém confirmação, limites e cancelamento. As regras de persona/contexto são configuração da app, não treino dos pesos.

## Referências de builders

[Osaurus](https://github.com/osaurus-ai/osaurus/blob/main/README.md) suporta a família LFM e separa o serviço de inferência da execução de ferramentas pelo cliente. Não é exclusivo destes modelos. O [guia MCP do Osaurus](https://osaurus.ai/guides/mcp-setup) descreve o ciclo de pedido, autorização, ferramenta e resposta; adotamos essa separação, sem adicionar MCP ao Android nesta etapa.

O [LocalCowork da Liquid AI](https://www.liquid.ai/blog/no-cloud-tool-calling-agents-consumer-hardware-lfm2-24b-a2b) avalia seleção de ferramentas e cadeias guiadas. É um caso de outro modelo e de hardware desktop; os seus resultados e parâmetros não devem ser apresentados como desempenho do nosso 350M no telemóvel.

Atualização de 8 de setembro: o utilizador identificou Insilico Medicine. O comunicado refere LFM2-2.6B-MMAI para investigação farmacêutica, não o nosso 350M. Ver [revisão das referências e decisão Android](2026-09-08-android-pet-decision.md).

## Regra de evolução

Primeiro preservar este protocolo, congelar a referência e executar conversas sem ferramentas, chamadas reais, observações e respostas finais. Depois afinar prompts com um conjunto de avaliação variado e medir a diferença. Tiny e Maxx permanecem modos do mesmo papel: o runtime controla o flow e os limites; a persona não pode conceder permissões.
