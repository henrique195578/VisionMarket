package com.mercado.orcamento.config;

import com.mercado.orcamento.model.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class PrecoPagoSchema implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public PrecoPagoSchema(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        try (var connection=jdbc.getDataSource().getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")) return;
        }
        // Amplia somente os CHECKs dos enums conhecidos, preservando os registros.
        var checks=jdbc.queryForList("SELECT conname,t.relname AS tabela,pg_get_constraintdef(c.oid) AS definicao "
                +"FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace "
                +"WHERE t.relname IN ('registro_preco','nota_fiscal') AND n.nspname=current_schema() AND c.contype='c'");
        for (Map<String,Object> check:checks) {
            String def=String.valueOf(check.get("definicao")), coluna=null, valores=null;
            if (def.contains("tipo_preco") && def.contains("VAREJO") && def.contains("ATACADO") && !def.contains("PAGO")) {
                coluna="tipo_preco";
                valores=Arrays.stream(TipoPreco.values()).map(v->"'"+v.name()+"'").collect(Collectors.joining(","));
            } else if (def.contains("mercado") && def.contains("ATACADAO") && !def.contains("ASSAI")) {
                coluna="mercado";
                valores=Arrays.stream(Mercado.values()).map(v->"'"+v.name()+"'").collect(Collectors.joining(","));
            }
            if (coluna==null) continue;
            String nome=String.valueOf(check.get("conname")).replace("\"","\"\"");
            String tabela=String.valueOf(check.get("tabela"));
            jdbc.execute("ALTER TABLE "+tabela+" DROP CONSTRAINT \""+nome+"\"");
            jdbc.execute("ALTER TABLE "+tabela+" ADD CONSTRAINT \""+nome+"\" CHECK ("+coluna+" IN ("+valores+"))");
        }
    }
}
