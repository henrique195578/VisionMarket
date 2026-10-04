(function () {
'use strict';
const $ = id => document.getElementById(id);
const itens = JSON.parse(window.VISION_ITENS || '[]');
const regras = window.VisionComparacao;
const moeda = valor => Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 4 });
let registros = [], busca = 0, upload = 0, fotoUrl = null;
function guardar(chave, valor) { try { localStorage.setItem(chave, valor); } catch (_) {} }
function ler(chave) { try { return localStorage.getItem(chave); } catch (_) { return null; } }
function etapa(nome) {
    if (!['lista', 'foto', 'comparar', 'leitorqr', 'minhasnotas'].includes(nome)) nome = 'lista';
    document.body.dataset.etapa = nome;
    document.querySelectorAll('.section').forEach(el => { el.hidden = el.id !== nome; });
    document.querySelectorAll('[data-step]').forEach(el => el.classList.toggle('active', el.dataset.step === nome));
    const url = new URL(location.href); url.searchParams.set('etapa', nome); history.replaceState(null, '', url);
    document.dispatchEvent(new CustomEvent('vision:etapa', { detail: nome }));
    if (nome === 'comparar' && $('produtoComparar').value) carregarHistorico();
}
function limparPrecos() {
    ['varejo', 'atacado', 'cartao', 'minimo', 'codigo', 'condicoes', 'arquivo'].forEach(id => { $(id).value = ''; });
    $('confirmar').checked = false; $('fotoPreview').hidden = true; $('ocrDetails').hidden = true;
    $('ocrStatus').textContent = ''; $('camera').value = '';
}
function selecionarProduto(id) {
    $('produtoFoto').value = String(id);
    $('produtoComparar').value = String(id);
    const produto = itens.find(i => String(i.id) === String(id));
    $('qtdComparar').value = produto && produto.quantidadeDesejada > 0 ? produto.quantidadeDesejada : 1;
}
function dataTexto(r) {
    if (!r.dataRegistro) return 'Data não informada';
    const data = new Date(r.dataRegistro);
    if (!Number.isFinite(data.getTime())) return 'Data não informada';
    const hoje = new Date();
    let meses = (hoje.getFullYear() - data.getFullYear()) * 12 + hoje.getMonth() - data.getMonth();
    if (hoje.getDate() < data.getDate()) meses--;
    const dias = Math.max(0, Math.floor((hoje - data) / 86400000));
    const idade = meses > 0 ? 'há ' + meses + (meses === 1 ? ' mês' : ' meses')
        : dias > 0 ? 'há ' + dias + (dias === 1 ? ' dia' : ' dias') : 'hoje';
    return data.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) + ' · ' + idade;
}
function nomeMercado(key) {
    const option = [...$('mercadoFoto').options].find(o => o.value === key);
    return option ? option.textContent : key;
}
function condicoesTexto(r) {
    const partes = [];
    if (r.notaFiscalId) partes.push('Preço por ' + (r.unidadeMedida || 'UN') + ' · Quantidade na nota: ' + r.quantidadeCompra);
    if (r.tipoPreco === 'ATACADO') partes.push(r.quantidadeMinima ? 'A partir de ' + r.quantidadeMinima + ' unidades' : 'Quantidade mínima não registrada');
    if (r.tipoPreco === 'CARTAO') partes.push('Exige cartão / clube deste mercado');
    if (r.condicoes) partes.push(r.condicoes);
    return partes.join(' · ') || '—';
}
function texto(tag, value, classe) {
    const el = document.createElement(tag); el.textContent = value; if (classe) el.className = classe; return el;
}
function oferta(id, r) {
    const el = $(id); el.replaceChildren();
    if (!r) { el.append(texto('p', 'Nenhum preço atende ao filtro. Confira a quantidade, os cartões ou visualize todos os registros.', 'muted small')); return; }
    el.append(texto('div', moeda(r.valor), 'price'));
    el.append(texto('div', nomeMercado(r.mercado) + ' · ' + (r.unidade || 'Filial não informada')));
    el.append(texto('div', regras.tipos[r.tipoPreco] + ' · ' + dataTexto(r), 'muted small'));
    el.append(texto('div', condicoesTexto(r), 'muted small'));
}
function tabela(id, dados, foto) {
    const tbody = $(id); tbody.replaceChildren();
    dados.forEach(r => {
        const tr = document.createElement('tr');
        tr.append(texto('td', nomeMercado(r.mercado) + ' · ' + (r.unidade || 'Filial não informada')),
            texto('td', regras.tipos[r.tipoPreco] || r.tipoPreco), texto('td', moeda(r.valor)),
            texto('td', dataTexto(r)));
        const td = texto('td', condicoesTexto(r));
        if (foto && r.nomeArquivoImagem) {
            td.append(document.createElement('br'));
            const a = texto('a', 'Ver etiqueta'); a.href = '/imagens/' + encodeURIComponent(r.nomeArquivoImagem);
            a.target = '_blank'; a.rel = 'noopener'; td.append(a);
        }
        tr.append(td); tbody.append(tr);
    });
    if (!dados.length) {
        const tr = document.createElement('tr'), td = texto('td', 'Nenhum registro neste filtro.');
        td.colSpan = 5; tr.append(td); tbody.append(tr);
    }
}
function renderizar() {
    const cartoes = [...document.querySelectorAll('.cartaoPref:checked')].map(el => el.value);
    const resultado = regras.comparar(registros, $('modo').value, Number($('qtdComparar').value || 1), cartoes);
    $('resultado').hidden = !registros.length;
    $('comparacaoStatus').textContent = registros.length ? registros.length + ' preços registrados. Leituras antigas permanecem disponíveis.'
        : 'Ainda não há leituras para este produto. Fotografe a primeira etiqueta.';
    oferta('menorHistorico', resultado.menorHistorico); oferta('menorRecente', resultado.menorRecente);
    tabela('ultimasLeituras', resultado.ultimas, false); tabela('historico', resultado.historico, true);
}
async function carregarHistorico() {
    const id = $('produtoComparar').value, atual = ++busca;
    $('resultado').hidden = true;
    if (!id) { $('comparacaoStatus').textContent = 'Selecione o produto para consultar os preços.'; return; }
    $('comparacaoStatus').textContent = 'Consultando o histórico…';
    try {
        const response = await fetch('/api/produtos/' + encodeURIComponent(id) + '/historico');
        if (!response.ok || response.redirected) throw new Error('Não foi possível consultar o histórico. Recarregue a página e tente novamente.');
        const dados = await response.json();
        if (atual !== busca) return;
        registros = dados; renderizar();
    } catch (err) { if (atual === busca) $('comparacaoStatus').textContent = err.message; }
}
function mostrarFoto(arquivo) {
    $('arquivo').value = arquivo;
    $('fotoPreview').src = '/imagens/' + encodeURIComponent(arquivo); $('fotoPreview').hidden = false;
}
function aplicarOcr(dados) {
    mostrarFoto(dados.nomeArquivoImagem);
    const campos = { varejo: 'precoVarejo', atacado: 'precoAtacado', cartao: 'precoCartao', minimo: 'quantidadeMinima', codigo: 'codigoBarras' };
    Object.entries(campos).forEach(([id, chave]) => { if (dados[chave] != null) $(id).value = dados[chave]; });
    $('ocrText').textContent = dados.textoBruto || 'Nenhum texto reconhecido.';
    $('ocrDetails').hidden = false;
    const encontrou = dados.precoVarejo || dados.precoAtacado || dados.precoCartao;
    $('ocrStatus').textContent = encontrou ? 'Foto recebida. Confira cada preço sugerido e as condições antes de salvar.'
        : 'Foto recebida. Não foi possível separar os preços com segurança. Preencha os valores olhando a etiqueta.';
}
document.querySelectorAll('[data-step]').forEach(el => el.addEventListener('click', () => etapa(el.dataset.step)));
document.querySelectorAll('[data-capturar]').forEach(el => el.addEventListener('click', () => {
    limparPrecos(); selecionarProduto(el.dataset.capturar); etapa('foto'); window.scrollTo(0, 0);
}));
document.querySelectorAll('[data-remover]').forEach(el => el.addEventListener('click', async () => {
    el.disabled = true;
    try {
        const response = await fetch('/atualizarListaCompras?idItem=' + el.dataset.remover + '&naLista=false', { method: 'POST' });
        if (!response.ok || response.redirected) throw new Error('Não foi possível retirar o item. Recarregue e tente novamente.');
        location.href = '/?etapa=lista';
    } catch (err) { alert(err.message); el.disabled = false; }
}));
$('produtoFoto').addEventListener('change', () => { limparPrecos(); selecionarProduto($('produtoFoto').value); });
$('produtoComparar').addEventListener('change', () => {
    const produto = itens.find(i => String(i.id) === $('produtoComparar').value);
    $('qtdComparar').value = produto && produto.quantidadeDesejada > 0 ? produto.quantidadeDesejada : 1;
    carregarHistorico();
});
['mercadoFoto', 'unidade'].forEach(id => {
    const salvo = ler('vision.' + id); if (salvo) $(id).value = salvo;
    $(id).addEventListener('change', () => guardar('vision.' + id, $(id).value));
});
$('camera').addEventListener('change', async () => {
    const file = $('camera').files[0]; if (!file) return;
    if (!$('produtoFoto').value || !$('mercadoFoto').value || !$('unidade').value.trim()) {
        $('ocrStatus').textContent = 'Selecione produto, mercado e filial antes de fotografar.'; $('camera').value = ''; return;
    }
    const atual = ++upload;
    ['varejo', 'atacado', 'cartao', 'minimo', 'codigo', 'condicoes', 'arquivo'].forEach(id => { $(id).value = ''; });
    $('confirmar').checked = false; $('ocrDetails').hidden = true;
    if (fotoUrl) URL.revokeObjectURL(fotoUrl);
    fotoUrl = URL.createObjectURL(file); $('fotoPreview').src = fotoUrl; $('fotoPreview').hidden = false;
    $('salvarLeitura').disabled = true; $('produtoFoto').disabled = true; $('mercadoFoto').disabled = true; $('unidade').readOnly = true;
    $('ocrStatus').textContent = 'Recebendo foto e lendo a etiqueta…';
    const form = new FormData(); form.append('file', file);
    try {
        const response = await fetch('/api/fotos', { method: 'POST', body: form });
        if (response.redirected) throw new Error('Recarregue a página para renovar a sessão e fotografe novamente.');
        const dados = await response.json();
        if (!response.ok) throw new Error(dados.erro || 'Falha ao enviar a foto.');
        if (atual === upload) aplicarOcr(dados);
    } catch (err) { $('ocrStatus').textContent = 'Falha ao receber a foto. ' + err.message; }
    finally {
        if (atual === upload) {
            $('salvarLeitura').disabled = false; $('produtoFoto').disabled = false; $('mercadoFoto').disabled = false; $('unidade').readOnly = false;
        }
    }
});
$('atacado').addEventListener('input', () => { $('minimo').required = !!$('atacado').value; });
$('leituraForm').addEventListener('submit', event => {
    const erro = !$('arquivo').value ? 'Fotografe ou envie a etiqueta antes de salvar.'
        : !['varejo', 'atacado', 'cartao'].some(id => $(id).value) ? 'Informe pelo menos um preço da etiqueta.'
        : $('atacado').value && !Number($('minimo').value) ? 'Informe a quantidade mínima do atacado.' : '';
    if (erro) { event.preventDefault(); $('ocrStatus').textContent = erro; }
    else { $('salvarLeitura').disabled = true; $('salvarLeitura').textContent = 'Salvando leitura…'; }
});
['modo', 'qtdComparar'].forEach(id => $(id).addEventListener('change', renderizar));
let cartoes = [];
try { cartoes = JSON.parse(ler('vision.cartoes') || '[]'); } catch (_) {}
document.querySelectorAll('.cartaoPref').forEach(el => {
    el.checked = Array.isArray(cartoes) && cartoes.includes(el.value);
    el.addEventListener('change', () => {
        guardar('vision.cartoes', JSON.stringify([...document.querySelectorAll('.cartaoPref:checked')].map(c => c.value))); renderizar();
    });
});
const params = new URLSearchParams(location.search);
if (params.get('produto')) selecionarProduto(params.get('produto'));
const rascunho = window.VISION_RASCUNHO;
if (rascunho) {
    selecionarProduto(rascunho.idProduto);
    const campos = { mercadoFoto: 'mercado', unidade: 'unidade', varejo: 'precoVarejo', atacado: 'precoAtacado', cartao: 'precoCartao',
        minimo: 'quantidadeMinima', condicoes: 'condicoes', arquivo: 'arquivo', codigo: 'codigoBarras' };
    Object.entries(campos).forEach(([id, chave]) => { $(id).value = rascunho[chave] == null ? '' : rascunho[chave]; });
    if (rascunho.arquivo) mostrarFoto(rascunho.arquivo);
}
if (window.VISION_FOTO) {
    fetch('/ocr/' + encodeURIComponent(window.VISION_FOTO)).then(r => r.json()).then(aplicarOcr)
        .catch(() => { mostrarFoto(window.VISION_FOTO); $('ocrStatus').textContent = 'Confira e preencha os valores da foto.'; });
}
etapa(params.get('etapa') || (params.get('tab') === 'valores' ? 'comparar' : 'lista'));
})();
