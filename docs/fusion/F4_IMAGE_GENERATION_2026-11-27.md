# F4 — Geração real de imagens (NVIDIA FLUX.1-schnell)

Data: 27/11/2026. Base: `595d73d`. Licença do GabiTama: MIT (decisão do proprietário). Custo autorizado: **zero**. API paga: **não autorizada**.

## Cadeia implementada

`Chat → TaskOrchestrator → PolicyGate (confirmação por pedido) → ToolExecutorAndVerifier → ImageGenerationExecutor → NvidiaFluxSchnellProvider → NVIDIA NIM → PNG/JPEG decodificado e verificado → images/<uuid>.png no workspace privado (releitura + SHA-256) → recibo no chat → pré-visualização`.

- Gatilho explícito: `Gabi, cria uma imagem de …` (também `crie`, `gere`, `faça`, `desenhe`). Afirmações gerais sobre imagens não disparam geração.
- Ferramenta: `generate_image` (parâmetro único `prompt`). Referência, edição e parâmetros extras são recusados.
- Credencial: cofre existente (`SecureKeyVault.KEY_NVIDIA_API_KEY`), campo em Ajustes > Chaves de API. Enviada apenas no cabeçalho `Authorization: Bearer`, nunca na URL nem em logs.

## Provedor

| Item | Valor |
|---|---|
| Provedor | NVIDIA NIM |
| Modelo | black-forest-labs/flux.1-schnell |
| Endpoint | `https://ai.api.nvidia.com/v1/genai/black-forest-labs/flux.1-schnell` (POST, JSON) |
| Parâmetros | height=1024, width=1024, cfg_scale=0, mode=base, image=null, samples=1, seed≥0, steps 1–4, prompt ≤ 10000 caracteres |
| Custo | Rota de teste; sem cobrança autorizada. Termos: NVIDIA API Trial Terms (não confirmados para a conta) |

Catálogo (`ImageProviderCatalog`): Gemini Image está registrado como **não roteável** (custo pago não autorizado). Código do Gemini preservado em `GeminiImageGeneration` e testado, sem uso pela ferramenta.

## Validação da resposta

- Formatos aceitos: `artifacts[0].base64` (com `finishReason` = SUCCESS) ou `image` (base64, com ou sem prefixo `data:`).
- Qualquer outro formato é recusado como `INVALID_RESPONSE`.
- Decodificação estrita; assinatura PNG/JPEG; dimensões lidas dos bytes (IHDR/SOF); limites de tamanho.

## Erros tratados

`CREDENTIAL_MISSING`, `ACCESS_DENIED` (401/403), `QUOTA_EXCEEDED` (402/429), `TIMEOUT`, `PROVIDER_UNAVAILABLE` (404, 5xx, rede), `INVALID_REQUEST` (400/422), `INVALID_RESPONSE`, `SAVE_FAILED`, `NOT_AUTHORIZED`. Cada categoria tem mensagem própria; nenhuma mensagem declara sucesso sem arquivo salvo.

## Chat e entrega

- O recibo usa o formato `Imagem criada e salva em images/<uuid>.png; SHA-256 …. Abra na Biblioteca.`, único que a pré-visualização aceita.
- Botões **Compartilhar** (FileProvider, leitura concedida) e **Salvar na galeria** (MediaStore, `Pictures/GabiTama`, somente após toque; Android 10+).

## Testes

- JVM (controlados, sem gasto): `NvidiaFluxSchnellProviderUnitTest` (corpo, validação, formatos de resposta, PNG/JPEG, HTTP 401/403/402/429/404/422/503, timeout, chave ausente sem rede), `ImageGenerationExecutorUnitTest` (consentimento, custo pago, credencial, releitura de hash, ponta a ponta com servidor simulado), `ImageRequestIntentCriaUnitTest`.
- **Não executados localmente** (sem JDK/Gradle no sandbox); a verificação é a CI `technical-validation.yml`.

## Pendente (não validado)

- **Geração real na NVIDIA: pendente.** O sandbox não alcança `ai.api.nvidia.com` e não há credencial de desenvolvimento autorizada. Não foi gasto nada.
- Formato exato da resposta 200 não está detalhado na página da documentação consultada; os dois formatos acima são suportados e serão confirmados na primeira chamada real.
- Disponibilidade da conta, cota e condições de uso comercial: não confirmadas. Acesso gratuito de teste não autoriza uso comercial em produção.
- Teste em aparelho: não feito. Salvamento na galeria não validado em Android 10+.
- Comando livre (sem o gatilho "Gabi, cria uma imagem…") ainda não passa pelo modelo para acionar a ferramenta.
