/* Regras compartilhadas pela tela e pelos testes. Nenhuma leitura expira por idade. */
(function (root) {
    'use strict';
    const tipos = { VAREJO: 'Varejo', ATACADO: 'Atacado', CARTAO: 'Cartão / clube', PAGO: 'Pago na nota fiscal' };
    function timestamp(r) { return r.dataRegistro ? Date.parse(r.dataRegistro) || 0 : 0; }
    function ordenar(a, b) { return timestamp(b) - timestamp(a) || Number(b.id || 0) - Number(a.id || 0); }
    function chave(r) {
        return JSON.stringify([r.mercado, (r.unidade || 'Filial não informada').trim().toLocaleUpperCase('pt-BR'), r.tipoPreco]);
    }
    function ultimas(registros) {
        const vistos = new Map();
        registros.slice().sort(ordenar).forEach(r => { if (!vistos.has(chave(r))) vistos.set(chave(r), r); });
        return [...vistos.values()];
    }
    function permitido(r, modo, quantidade, cartoes) {
        if (!(Number(r.valor) > 0)) return false;
        if (modo === 'todos') return true;
        if (modo !== 'aplicaveis') return r.tipoPreco === modo;
        if (r.tipoPreco === 'CARTAO') return cartoes.includes(r.mercado);
        if (r.tipoPreco === 'ATACADO') return Number(r.quantidadeMinima) >= 1 && quantidade >= Number(r.quantidadeMinima);
        return r.tipoPreco === 'VAREJO';
    }
    function menor(registros) {
        return registros.slice().sort((a, b) => Number(a.valor) - Number(b.valor) || ordenar(a, b))[0] || null;
    }
    function comparar(registros, modo, quantidade, cartoes) {
        const elegiveis = registros.filter(r => permitido(r, modo, quantidade, cartoes));
        const recentes = ultimas(registros).filter(r => permitido(r, modo, quantidade, cartoes));
        return { historico: registros.slice().sort(ordenar), ultimas: recentes, menorHistorico: menor(elegiveis), menorRecente: menor(recentes) };
    }
    const api = { tipos, comparar, ultimas, permitido };
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    root.VisionComparacao = api;
})(typeof window === 'undefined' ? globalThis : window);
