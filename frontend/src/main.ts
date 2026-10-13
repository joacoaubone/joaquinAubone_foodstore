import './style.css'

const app = document.querySelector<HTMLElement>('#app')

if (!app) {
  throw new Error('No se encontró el elemento raíz #app')
}

app.innerHTML = `
  <main class="app-shell">
    <h1>Food Store</h1>
    <p>La aplicación se inicializó correctamente.</p>
  </main>
`
