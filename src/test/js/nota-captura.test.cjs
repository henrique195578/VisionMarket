const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
test('formata chave, valida dígito e alterna tela manual encerrando câmera',()=>{
 const elements={},events={},dispatch=[];
 const get=id=>elements[id]||(elements[id]={hidden:false,value:'',selectionStart:0,dataset:{},disabled:false,listeners:{},addEventListener(k,f){this.listeners[k]=f},click(){this.clicks=(this.clicks||0)+1},focus(){},setSelectionRange(a){this.selectionStart=a}});
 const document={getElementById:get,querySelectorAll:()=>[],querySelector:()=>get('lista'),body:{classList:{toggle(){}}},addEventListener:(k,f)=>events[k]=f,dispatchEvent:e=>dispatch.push(e)};
 vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/nota-captura.js','utf8'),{document,CustomEvent:class{constructor(type,o){this.type=type;this.detail=o.detail}}});
 get('notaDigitarManual').listeners.click();assert.equal(get('leitorqr').dataset.captura,'manual');assert.ok(get('notaPararCamera').clicks);
 const key='35261006057223030755650110000447971110028220';
 get('notaChave').value=key;get('notaChave').selectionStart=44;get('notaChave').listeners.input();
 assert.equal(get('notaChave').value,key.match(/.{4}/g).join(' '));assert.equal(get('notaEnviarChave').disabled,false);
 get('notaChaveForm').listeners.submit({preventDefault(){}});assert.equal(dispatch[0].detail,key);
 get('notaChave').value=key.slice(0,-1)+'1';get('notaChave').listeners.input();assert.equal(get('notaEnviarChave').disabled,true);
});
