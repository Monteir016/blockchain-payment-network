# DOCUMENTO QUE ACOMPANHA A 3ª ENTREGA DO PROJETO DE SD

1) Quais dos seguintes requisitos foram corretamente resolvidos?
Abaixo de cada requisito, respondam: "Sim" ou "Não" ou "Sim mas com erros: [enumerar sucintamente os erros]"

- Suporta a execução/resposta a pedidos de transferência antes da transação correspondente ser entregue pelo sequenciador [C.1].
Sim

- As transações são assinadas digitalmente pelo utilizador que as invocou e as respetivas assinaturas são verificadas antes de cada transação ser executada nas réplicas [C.2].
Sim

- A blockchain gerada pelo sequenciador é assinada digitalmente e as assinaturas correspondentes são verificadas pelos nós que recebem cada bloco [C.2].
Sim

2) Descrevam sucintamente as principais alterações que o grupo aplicou a cada componente indicada abaixo (máx. 800 palavras)
Idealmente, componham uma lista de itens (cada um iniciado por um hífen, tal como a listagem dos requisitos apresentada acima).
Refiram ainda se houve alterações aos argumentos de linha de comando dos executáveis, quais foram e qual a sua justificação.

Aos .proto:

- Adição da signature nas mensagens Request das operações (C/E/T) para suportar a assinatura digital do cliente

- Adição da signature e do signer_id na mensagem do bloco para suportar a assinatura digital do sequenciador


Ao programa do Cliente:

- Implementação de um mecanismo de leitura da chave privada do utilizador

- Implementação de "canonicalização" dos pedidos como objeto: antes de assinar, o cliente converte o pedido para bytes ignorando o campo da assinatura

- Atualização dos pedidos de escrita usando assinatura digital


Ao programa do Nó:

- Registo e propagação do argumento 'organization', garantindo que cada nó está associado a apenas uma organização

- Implementação de uma HashMap que associa cada utilizador à sua respetiva organização (por predefinição, como indicado no enunciado)

- Verificação dos acessos no início das chamadas gRPC, rejeitando pedidos cujos emissores não pertençam à organização do nó, através de um novo método

- Otimização do método transfer garantindo ordem causal: se a carteira de destino pertencer à organização do nó, a transferência é aplicada imediatamente, notificando o cliente, antes do bloco ser recebido

- Criação do ficheiro RequestSignatureVerifier para gestão e validação das assinaturas digitais dos clientes

- Criação do ficheiro BlockSignatureVerifier para validar a assinatura digital do sequenciador, usado na ApplicationPipeline para aceitar o bloco antes de aplicar as transações no nó


Ao programa do Sequenciador:

- Criação da classe BlockSigner para assinar blocos com a chave privada do sequenciador
- Atualização da lógica de fecho de blocos, assinando-os digitalmente antes de os enviar aos nós


3) Na vossa solução para o requisito da C.1, em quais condições uma transferência **NÃO** é executada pelo nó (que recebeu o pedido respetivo) antes da transação ser enviada ao sequenciador? (máx. 100 palavras)

Uma transferência otimista não é executada imediatamente se a carteira de destino não pertencer à organização do nó que recebeu o pedido. Nestes casos, o nó opta pelo fluxo convencional, aguardando que o bloco seja entregue pelo sequenciador antes de responder ao cliente. Esta abordagem previne incoerências (como falsos positivos de saldo) no caso da operação de eliminar a carteira de destino, iniciada concorrentemente noutro nó.


4) Como foi frisado na primeira aula teórica, não é aceitável o uso de GenAI/Code Copilots para gerar código usado diretamente no projeto.
Tendo isto em conta, respondam brevemente às seguintes 3 perguntas:

i) Os membros do grupo conseguem explicar o código submetido se tal for perguntado na discussão do projeto (feita sem recurso a AI)?
Sim

ii) Os membros do grupo conseguem defender todas as decisões de desenho e implementação se tal for perguntado na discussão do projeto (feita sem recurso a AI)?
SIm

iii) Usaram alguma(s) ferramenta(s) de AI? Se sim, para que uso?
Foram utilizadas ferramentas de AI apenas como apoio à compreensão do enunciado e dos conceitos associados, bem como para a organização do trabalho e identificação de problemas. Não foi utilizado código gerado por AI diretamente na implementação do projeto.