const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const source = fs.readFileSync('src/main/resources/static/js/atualizacao.js','utf8');
async function iniciar(build, confirmacao=true, offline=false) {
 const handlers={}, aviso={hidden:true}, botao={addEventListener:(n,f)=>handlers.click=f};
 let destino=null, chamadas=0;
 const contexto={
  document:{hidden:false,querySelector:()=>({content:'antiga'}),getElementById:id=>id==='avisoAtualizacao'?aviso:botao,addEventListener:(n,f)=>handlers[n]=f},
  navigator:{onLine:!offline},
  window:{confirm:()=>confirmacao,addEventListener:(n,f)=>handlers[n]=f},
  location:{href:'https://mercado.test/?etapa=lista',replace:u=>destino=u},
  URL,AbortController,setTimeout:()=>1,clearTimeout:()=>{},setInterval:()=>{},
  fetch:async()=>{chamadas++;return {ok:true,redirected:false,json:async()=>({build})}}
 };
 vm.runInNewContext(source,contexto);
 await new Promise(resolve=>setImmediate(resolve));
 return {aviso,handlers,destino:()=>destino,chamadas:()=>chamadas};
}
test('versao igual mantém botão oculto',async()=>{const x=await iniciar('antiga');assert.equal(x.aviso.hidden,true);assert.equal(x.destino(),null)});
test('nova versão só atualiza ao clicar',async()=>{const x=await iniciar('nova');assert.equal(x.aviso.hidden,false);assert.equal(x.destino(),null);x.handlers.click();const u=new URL(x.destino());assert.equal(u.searchParams.get('versao'),'nova');assert.equal(u.searchParams.get('etapa'),'lista')});
test('cancelar mantém tela atual',async()=>{const x=await iniciar('nova',false);x.handlers.click();assert.equal(x.destino(),null)});
test('offline não dispara requisição',async()=>{const x=await iniciar('nova',true,true);assert.equal(x.chamadas(),0);assert.equal(x.aviso.hidden,true)});
