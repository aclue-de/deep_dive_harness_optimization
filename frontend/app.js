// Warehouse app frontend: loads items, books stock, creates items.
const API = "/api"; // served by backend/Server.java

const message = document.getElementById("message");
const tbody = document.getElementById("items");

async function call(path, options = {}) {
  const response = await fetch(API + path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const body = await response.json();
  if (!response.ok) throw new Error(body.error);
  return body;
}

function row(item) {
  const tr = document.createElement("tr");
  tr.innerHTML = `<td>${item.sku}</td><td>${item.name}</td><td>${item.quantity}</td>
    <td><button data-delta="1">+1</button> <button data-delta="-1">−1</button></td>`;
  tr.querySelectorAll("button").forEach((button) =>
    button.addEventListener("click", () => book(item.sku, Number(button.dataset.delta)))
  );
  return tr;
}

async function load() {
  const items = await call("/items");
  tbody.replaceChildren(...items.map(row));
}

async function book(sku, delta) {
  try {
    await call(`/items/${sku}/stock`, { method: "POST", body: JSON.stringify({ delta }) });
    message.textContent = "";
    await load();
  } catch (error) {
    message.textContent = error.message;
  }
}

document.getElementById("new-item").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  try {
    await call("/items", {
      method: "POST",
      body: JSON.stringify({
        sku: form.get("sku"),
        name: form.get("name"),
        quantity: Number(form.get("quantity")),
      }),
    });
    event.target.reset();
    message.textContent = "";
    await load();
  } catch (error) {
    message.textContent = error.message;
  }
});

load().catch(() => (message.textContent = "Backend not reachable"));
