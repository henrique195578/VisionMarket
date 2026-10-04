(function(){
'use strict';
const $=id=>document.getElementById(id), moeda=n=>Number(n).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
let nota=null, requisicao=0;
function el(tag,texto,classe){const x=document.createElement(tag);x.textContent=texto||'';if(classe)x.className=classe;return x;}
function data(valor,horario){if(!valor)return 'Data não informada';return new Date(valor).toLocaleString('pt-BR',horario?{dateStyle:'short',timeStyle:'short'}:{dateStyle:'short'});}
async function json(response){if(response.redirected)throw Error('Sua sessão expirou. Volte à lista para renovar o acesso.');if(!response.ok){let erro;try{erro=(await response.json()).erro;}catch(_){}throw Error(erro||'Não foi possível carregar a nota.');}return response.json();}
async function carregar(){
 const versao=++requisicao;$('minhaNotaDetalhe').hidden=true;$('minhasNotasLista').hidden=false;$('minhasNotasStatus').textContent='Carregando notas…';
 try{
 const notas=await json(await fetch('/api/minhas-notas',{cache:'no-store'}));if(versao!==requisicao)return;
 $('minhasNotasLista').replaceChildren();let mesAnterior='';
 notas.forEach(n=>{
  const mes=n.dataCompra?new Date(n.dataCompra).toLocaleDateString('pt-BR',{month:'long',year:'numeric'}):'Data não informada';
  if(mes!==mesAnterior){$('minhasNotasLista').append(el('h2',mes,'h6 muted mt-4 mb-3'));mesAnterior=mes;}
  const card=el('button','','cupom-card');card.type='button';card.setAttribute('aria-label','Ver nota de '+n.nome);
  const info=el('div','','cupom-info');info.append(el('strong',n.nome),el('div',data(n.dataCompra),'small muted'),el('span','Concluída','cupom-status'));
  const valor=el('div','','cupom-valor');valor.append(el('strong',moeda(n.total)),el('div',n.quantidadeItens+' itens','small muted'));
  card.append(el('span','▤','cupom-icone'),info,valor);card.addEventListener('click',()=>detalhe(n.id));$('minhasNotasLista').append(card);
 });
 $('minhasNotasStatus').textContent=notas.length?'':'Nenhuma nota importada ainda. Leia um QR Code ou importe um arquivo para começar.';
 }catch(e){if(versao===requisicao)$('minhasNotasStatus').textContent=e.message;}
}
async function detalhe(id){
 const versao=++requisicao;$('minhasNotasStatus').textContent='Carregando detalhes…';
 try{const dados=await json(await fetch('/api/minhas-notas/'+id,{cache:'no-store'}));if(versao!==requisicao)return;nota=dados;renderDetalhe();$('minhasNotasStatus').textContent='';}
 catch(e){if(versao===requisicao)$('minhasNotasStatus').textContent=e.message;}
}
function renderDetalhe(){
 const root=$('minhaNotaDetalhe');root.replaceChildren();root.hidden=false;$('minhasNotasLista').hidden=true;
 const voltar=el('button','← Todas as notas','btn btn-link mb-3');voltar.type='button';voltar.addEventListener('click',carregar);root.append(voltar,el('div','Concluída','cupom-status mb-3'));
 const cab=el('div','','nota-detalhe-card');cab.append(el('h2',nota.nome,'h4'),el('p',nota.unidade,'muted'),el('div','Data: '+data(nota.dataCompra,true)),el('strong','Total dos itens: '+moeda(nota.total),'d-block mt-2 text-success'));
 if(nota.totalEstimado)cab.append(el('p','Total reconstruído dos registros antigos; confira com o comprovante.','small muted mt-2'));
 root.append(cab);
 const aviso=el('div','','alert '+(nota.estabelecimentoConfirmado?'alert-light border':'alert-warning'));
 aviso.append(el('p',nota.estabelecimentoConfirmado?'Estabelecimento confirmado por você.':'Confira os dados do supermercado. O nome obtido da nota pode não ser o nome que você utiliza.'));
 const alterar=el('button','Alterar','btn btn-outline-dark');alterar.type='button';alterar.addEventListener('click',()=>{
  $('nomeMercadoNota').value=nota.nome;$('mercadoNotaSelecionado').value=nota.mercado||'';$('filialMercadoNota').value=nota.unidade||'';$('alterarMercadoStatus').textContent='';$('alterarMercadoDialog').showModal();
 });aviso.append(alterar);root.append(aviso);
 root.append(el('h3','Itens da nota','h6 muted mt-4'));
 const itens=el('div','','nota-detalhe-card');
 nota.itens.forEach(i=>{
 const linha=el('div','','nota-item-linha'),info=el('div');info.append(el('div',i.nome),el('div',(i.quantidade??'Quantidade não registrada')+' '+(i.unidade||'')+' × '+moeda(i.precoUnitario),'small muted mt-1'));
 linha.append(info,el('strong',i.total===null?'Total não informado':moeda(i.total)));itens.append(linha);
 });if(!nota.itens.length)itens.append(el('p','Os itens desta nota não estão disponíveis.'));root.append(itens);
 if(nota.arquivo){const a=el('a','Ver comprovante original','btn btn-outline-success mt-2');a.href='/imagens/'+encodeURIComponent(nota.arquivo);a.target='_blank';a.rel='noopener';root.append(a);}
 voltar.focus();
}
$('fecharMercadoDialog').addEventListener('click',()=>$('alterarMercadoDialog').close());
$('alterarMercadoForm').addEventListener('submit',async e=>{
 e.preventDefault();if(!nota)return;const botao=e.currentTarget.querySelector('button');botao.disabled=true;
 try{
 nota=await json(await fetch('/api/minhas-notas/'+nota.id+'/estabelecimento',{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({nome:$('nomeMercadoNota').value,mercado:$('mercadoNotaSelecionado').value,unidade:$('filialMercadoNota').value})}));
 $('alterarMercadoDialog').close();renderDetalhe();
 }catch(err){$('alterarMercadoStatus').textContent=err.message;}finally{botao.disabled=false;}
});
document.addEventListener('vision:etapa',e=>{if(e.detail==='minhasnotas')carregar();else{requisicao++;$('alterarMercadoDialog').close();}});
if(new URLSearchParams(location.search).get('etapa')==='minhasnotas')carregar();
})();
