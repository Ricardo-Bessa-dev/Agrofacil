// Tipo dos arquivos SQL lidos como texto pelos testes (importação "?raw" do Vite).
declare module "*.sql?raw" {
  const content: string;
  export default content;
}
