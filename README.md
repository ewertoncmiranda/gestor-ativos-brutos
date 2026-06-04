# Gestor de Ativos Brutos (Java Spring Boot)

Aplicação backend robusta desenhada em **Java com Spring Boot**. Seu propósito é atuar como API, ponto de integração ou serviço de domínio dentro do ecossistema de monitoramento financeiro da B3, fazendo a ponte entre sistemas externos, mensageria SQS e a camada de persistência.

## Responsabilidades
1. **Integração de Domínio**: Centralizar endpoints REST e fluxos de injeção ou consulta dos dados financeiros em tempo real.
2. **Mensageria (AWS SQS)**: Capaz de gerar/produzir payloads ou escutar eventos do barramento via abstrações de SQS do ecossistema Spring Cloud AWS.
3. **Persistência Centralizada (MySQL)**: Conecta-se à mesma fonte de dados relacional populada pelo worker (que gerou o schema via `mysql-init`), permitindo abstrair operações CRUD de negócio.

## Libs Principais Utilizadas
- **Spring Boot Core / Spring Web**: Facilitador para levantamento rápido do servidor HTTP e injeção de dependências.
- **Spring Data JPA / Hibernate**: Ferramenta de mapeamento ORM (Object-Relational Mapping) para interagir de forma performática com o MySQL.
- **MySQL Connector / JDBC Driver**: Responsável pela conexão com banco relacional nativa da JVM.
- **Spring Cloud AWS / AWS Java SDK**: Abstração limpa para publicação e leitura segura em filas AWS (SQS) e outros serviços via clients autoconfigurados.
- **Lombok**: Lib que reduz drasticamente código de *boilerplate* em classes de domínio (getters, setters, builders, logs).

## Variáveis de Ambiente e Configuração
A aplicação suporta parametrização dinâmica via variáveis de ambiente com fallbacks seguros.

| Variável | Padrão (Local)                                     | Descrição |
| --- |----------------------------------------------------| --- |
| `SPRING_PROFILES_ACTIVE` | `dev`                                              | Define qual perfil do Spring Boot será ativo. |
| `SERVER_PORT` | `9090`                                             | Porta onde o servidor HTTP do Spring Boot irá rodar. |
| `AWS_SQS_ENDPOINT_BASE` | `http://localstack:4566`                           | Endpoint da fila SQS AWS emulada (LocalStack). |
| `AWS_SQS_QUEUE_URL` | `http://localstack:4566/000000000000/tratar-ativos` | URL completa da fila SQS de processamento de ativos. |
| `AWS_ACCESS_KEY_ID` | `teste`                                            | Credencial AWS Access Key. |
| `AWS_SECRET_ACCESS_KEY` | `teste`                                            | Credencial AWS Secret Key. |
| `BRAPI_API_KEY` | `<SUA_CHAVE_AQUI>`                                   | Chave de acesso à API externa Brapi para cotações. |
| `DB_URL` | `jdbc:mysql://mysql:3306/minha_base?...`           | URL de conexão JDBC do banco de dados MySQL. |
| `DB_USERNAME` | `spring`                                           | Usuário de conexão do banco de dados. |
| `DB_PASSWORD` | `spring123`                                        | Senha de conexão do banco de dados. |

> [!NOTE]
> Você também pode enviar a chave do Gemini de forma dinâmica a cada requisição enviando o header HTTP `X-Gemini-Key` no endpoint `/insights/{simbolo}/analise`.

## Como Executar

Exemplo `.env` para execução local (dentro do compose)
```env
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8091
DB_URL=jdbc:mysql://mysql:3306/minha_base?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=spring
DB_PASSWORD=spring123
AWS_SQS_ENDPOINT_BASE=http://localstack:4566
AWS_SQS_QUEUE_URL=http://localstack:4566/000000000000/tratar-ativos
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
BRAPI_API_KEY=<sua-chave-brapi-aqui>
```

**1. Ambiente de Desenvolvimento Local (IDE / Maven)**
Para inicializar o Spring Boot apontando para a infraestrutura do seu docker (MySQL em 3305 e LocalStack em 4566):
```bash
# Baixa as dependências e empacota
./mvnw clean package

# Inicia o projeto com perfil de desenvolvimento
java -jar target/gestor-ativos-brutos-0.0.1-SNAPSHOT.jar
```

**2. Ambiente Docker**
Incluso no `docker-compose.yml`, o container subirá de maneira integrada usando o Dockerfile nativo do projeto, vinculando o banco MySQL e o SQS automaticamente sem conflitos de rede.

## Variáveis de ambiente (exemplo e uso em imagens)

Há um arquivo de exemplo `gestor-ativos-brutos/.env.example` com as variáveis necessárias para executar a imagem em ambientes locais ou CI. Copie esse arquivo para `.env` ou exporte as variáveis no seu ambiente antes de levantar o container.

Exemplo rápido:

```bash
cp gestor-ativos-brutos/.env.example gestor-ativos-brutos/.env
docker-compose up --build gestor-ativos-brutos
```

## Observações importantes
-   Se o banco não estiver disponível, o Spring pode falhar ao iniciar por causa de spring.jpa.hibernate.ddl-auto=validate em application.properties/application-dev.properties.
-   Ao executar a aplicação no host e o MySQL via compose, atente-se ao mapeamento de portas (docker-compose.yml mapeia 3305:3306); nesse caso use jdbc:mysql://localhost:3305/....
-   Actuator expõe endpoints detalhados quando management.endpoint.health.show-details=always está ativo no perfil dev.

## Dicas de troubleshooting
-   Ver logs: o container mapeia logs para ./logs/java. Veja docker logs gestor-ativos-brutos para saída.
-   Ver status do DB: docker inspect --format='{{json .State.Health}}' mysql ou docker logs mysql.
-   Fila não existe: rode o provisioner Terraform (já integrado no docker-compose.yml através do serviço terraform-provisioner) ou crie manualmente com awslocal.
