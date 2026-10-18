package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraAtivoRequest;
import br.com.miranda.gestor.ativos.brutos.external.dto.CarteiraDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.CriarCarteiraRequest;
import br.com.miranda.gestor.ativos.brutos.external.dto.ImportacaoCarteirasDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.ImportacaoCarteirasRequest;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCarteiras;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ServicoCarteiras {
    private static final Pattern COR = Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");
    private static final Pattern SIMBOLO = Pattern.compile("^[A-Z0-9]{4,12}$");
    private final RepositorioCarteiras repositorio;

    public ServicoCarteiras(RepositorioCarteiras repositorio) {
        this.repositorio = repositorio;
    }

    public List<CarteiraDTO> listar() {
        exigirSchema();
        return repositorio.listarCarteiras().stream().map(this::comAtivos).toList();
    }

    public CarteiraDTO buscar(String id) {
        exigirSchema();
        return comAtivos(repositorio.buscar(validarId(id)).orElseThrow(() -> naoEncontrada("Carteira não encontrada")));
    }

    @Transactional
    public CarteiraDTO criar(CriarCarteiraRequest request) {
        exigirSchema();
        DadosCarteira dados = validar(request);
        String id = UUID.randomUUID().toString();
        repositorio.criar(id, dados.nome(), dados.estrategia(), dados.cor(), dados.moeda());
        return buscar(id);
    }

    /**
     * Imports the legacy browser store without translating simulated allocations
     * into financial operations. Existing IDs are accepted only on exact match.
     */
    @Transactional
    public synchronized ImportacaoCarteirasDTO importar(ImportacaoCarteirasRequest request) {
        exigirSchema();
        if (request == null || request.carteiras() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "carteiras é obrigatório");
        }
        List<CarteiraImportada> carteiras = request.carteiras().stream().map(this::normalizarImportacao).toList();
        HashSet<String> ids = new HashSet<>();
        for (CarteiraImportada carteira : carteiras) {
            if (!ids.add(carteira.id())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID de carteira repetido no payload");
        }

        // Preflight all existing IDs before writing any rows: the whole batch either
        // matches its prior import or is inserted atomically, never overwritten.
        int existentes = 0;
        for (CarteiraImportada carteira : carteiras) {
            var atual = repositorio.buscar(carteira.id());
            if (atual.isPresent()) {
                if (!corresponde(atual.get(), carteira)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "ID de carteira já existe com conteúdo diferente: " + carteira.id());
                }
                existentes++;
            }
        }
        for (CarteiraImportada carteira : carteiras) {
            if (repositorio.buscar(carteira.id()).isPresent()) continue;
            repositorio.criarImportada(carteira.id(), carteira.nome(), carteira.estrategia(), carteira.cor(),
                    "BRL", carteira.criadaEm());
            for (var ativo : carteira.ativos()) {
                repositorio.salvarAtivo(carteira.id(), ativo.simbolo(), ativo.dataEntrada(), ativo.capital());
            }
        }
        Map<String, String> mapeamento = new TreeMap<>();
        carteiras.forEach(c -> mapeamento.put(c.id(), c.id()));
        return new ImportacaoCarteirasDTO(hashCanonico(carteiras), carteiras.size() - existentes, existentes, mapeamento);
    }

    @Transactional
    public CarteiraDTO atualizar(String id, CriarCarteiraRequest request) {
        exigirSchema();
        String carteiraId = validarId(id);
        DadosCarteira dados = validar(request);
        if (repositorio.buscar(carteiraId).filter(c -> "ATIVA".equals(c.status())).isEmpty()) {
            throw naoEncontrada("Carteira não encontrada ou arquivada");
        }
        repositorio.atualizar(carteiraId, dados.nome(), dados.estrategia(), dados.cor(), dados.moeda());
        return buscar(carteiraId);
    }

    @Transactional
    public void arquivar(String id) {
        exigirSchema();
        if (repositorio.arquivar(validarId(id)) == 0) throw naoEncontrada("Carteira não encontrada ou arquivada");
    }

    @Transactional
    public CarteiraDTO.AtivoDTO salvarAtivo(String id, String simbolo, CarteiraAtivoRequest request) {
        exigirSchema();
        String carteiraId = validarId(id);
        if (repositorio.buscar(carteiraId).filter(c -> "ATIVA".equals(c.status())).isEmpty()) {
            throw naoEncontrada("Carteira não encontrada ou arquivada");
        }
        if (request == null || request.dataEntrada() == null || request.capitalAlocado() == null
                || request.capitalAlocado().signum() <= 0 || request.capitalAlocado().scale() > 2
                || request.capitalAlocado().precision() > 18) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe dataEntrada e capitalAlocado positivo (até 2 casas decimais)");
        }
        String ticker = simbolo == null ? "" : simbolo.trim().toUpperCase(Locale.ROOT);
        if (!SIMBOLO.matcher(ticker).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Símbolo inválido");
        repositorio.salvarAtivo(carteiraId, ticker, request.dataEntrada(), request.capitalAlocado());
        return repositorio.listarAtivos(carteiraId).stream().filter(a -> a.simbolo().equals(ticker)).findFirst().orElseThrow();
    }

    @Transactional
    public void removerAtivo(String id, String simbolo) {
        exigirSchema();
        String carteiraId = validarId(id);
        if (repositorio.buscar(carteiraId).filter(c -> "ATIVA".equals(c.status())).isEmpty()) {
            throw naoEncontrada("Carteira não encontrada ou arquivada");
        }
        String ticker = simbolo == null ? "" : simbolo.trim().toUpperCase(Locale.ROOT);
        if (!SIMBOLO.matcher(ticker).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Símbolo inválido");
        if (repositorio.removerAtivo(carteiraId, ticker) == 0) {
            throw naoEncontrada("Alocação não encontrada");
        }
    }

    private CarteiraDTO comAtivos(CarteiraDTO carteira) {
        return new CarteiraDTO(carteira.id(), carteira.nome(), carteira.estrategia(), carteira.cor(),
                carteira.moedaBase(), carteira.status(), carteira.criadaEm(), repositorio.listarAtivos(carteira.id()));
    }

    private void exigirSchema() {
        if (!repositorio.schemaDisponivel()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Schema de Carteiras V42 não está disponível");
        }
    }

    private DadosCarteira validar(CriarCarteiraRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corpo obrigatório");
        String nome = texto(request.nome(), 120, "nome");
        String estrategia = texto(request.estrategia(), 80, "estrategia");
        String cor = texto(request.cor(), 7, "cor");
        if (!COR.matcher(cor).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cor deve usar #RGB ou #RRGGBB");
        String moeda = request.moedaBase() == null || request.moedaBase().isBlank()
                ? "BRL" : request.moedaBase().trim().toUpperCase(Locale.ROOT);
        if (!moeda.matches("^[A-Z]{3}$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "moedaBase deve ter 3 letras");
        return new DadosCarteira(nome, estrategia, cor, moeda);
    }

    private CarteiraImportada normalizarImportacao(ImportacaoCarteirasRequest.CarteiraLegada origem) {
        if (origem == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "carteira inválida no payload");
        String id = origem.id() == null ? "" : origem.id().trim();
        if (id.isEmpty() || id.length() > 64) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id de carteira deve ter até 64 caracteres");
        DadosCarteira dados = validar(new CriarCarteiraRequest(origem.nome(), origem.estrategia(), origem.cor(), "BRL"));
        LocalDateTime criadaEm = origem.criadaEm() == null ? null : origem.criadaEm().withNano(0);
        if (criadaEm != null && criadaEm.isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "criadaEm não pode estar no futuro");
        }
        List<AtivoImportado> ativos = new ArrayList<>();
        HashSet<String> simbolos = new HashSet<>();
        if (origem.ativos() != null) {
            for (var ativo : origem.ativos()) {
                if (ativo == null || ativo.dataEntrada() == null || ativo.capital() == null
                        || ativo.capital().signum() <= 0 || ativo.capital().scale() > 2 || ativo.capital().precision() > 18) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "alocação legada requer dataEntrada e capital positivo de até 2 casas");
                }
                String simbolo = ativo.simbolo() == null ? "" : ativo.simbolo().trim().toUpperCase(Locale.ROOT);
                if (!SIMBOLO.matcher(simbolo).matches() || !simbolos.add(simbolo)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "símbolo inválido ou repetido na carteira importada");
                }
                ativos.add(new AtivoImportado(simbolo, ativo.dataEntrada(), ativo.capital().setScale(2)));
            }
        }
        ativos.sort(Comparator.comparing(AtivoImportado::simbolo));
        return new CarteiraImportada(id, dados.nome(), dados.estrategia(), dados.cor(), criadaEm, List.copyOf(ativos));
    }

    private boolean corresponde(CarteiraDTO atual, CarteiraImportada importada) {
        if (!"ATIVA".equals(atual.status()) || !atual.nome().equals(importada.nome())
                || !atual.estrategia().equals(importada.estrategia()) || !atual.cor().equalsIgnoreCase(importada.cor())) return false;
        if (importada.criadaEm() != null && !importada.criadaEm().equals(atual.criadaEm().withNano(0))) return false;
        List<CarteiraDTO.AtivoDTO> existentes = repositorio.listarAtivos(importada.id());
        if (existentes.size() != importada.ativos().size()) return false;
        Map<String, CarteiraDTO.AtivoDTO> porSimbolo = new TreeMap<>();
        existentes.forEach(a -> porSimbolo.put(a.simbolo(), a));
        for (AtivoImportado ativo : importada.ativos()) {
            CarteiraDTO.AtivoDTO salvo = porSimbolo.get(ativo.simbolo());
            if (salvo == null || !salvo.dataEntrada().equals(ativo.dataEntrada())
                    || salvo.capitalAlocado().compareTo(ativo.capital()) != 0) return false;
        }
        return true;
    }

    private String hashCanonico(List<CarteiraImportada> carteiras) {
        StringBuilder canonico = new StringBuilder();
        carteiras.stream().sorted(Comparator.comparing(CarteiraImportada::id)).forEach(c -> {
            campo(canonico, c.id()); campo(canonico, c.nome()); campo(canonico, c.estrategia());
            campo(canonico, c.cor().toUpperCase(Locale.ROOT)); campo(canonico, c.criadaEm() == null ? null : c.criadaEm().toString());
            c.ativos().forEach(a -> { campo(canonico, a.simbolo()); campo(canonico, a.dataEntrada().toString()); campo(canonico, a.capital().toPlainString()); });
            campo(canonico, "fim-carteira");
        });
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonico.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private void campo(StringBuilder destino, String valor) {
        if (valor == null) destino.append("-1:");
        else destino.append(valor.length()).append(':').append(valor);
    }

    private String texto(String valor, int max, String campo) {
        if (valor == null || valor.isBlank() || valor.trim().length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, campo + " é obrigatório e deve ter até " + max + " caracteres");
        }
        return valor.trim();
    }

    private String validarId(String id) {
        if (id == null || id.isBlank() || id.length() > 64) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id inválido");
        return id;
    }

    private ResponseStatusException naoEncontrada(String mensagem) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, mensagem);
    }

    private record DadosCarteira(String nome, String estrategia, String cor, String moeda) { }
    private record AtivoImportado(String simbolo, java.time.LocalDate dataEntrada, BigDecimal capital) { }
    private record CarteiraImportada(String id, String nome, String estrategia, String cor,
                                     LocalDateTime criadaEm, List<AtivoImportado> ativos) { }
}
