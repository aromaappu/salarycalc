const drop = document.getElementById('dropzone');
const input = document.getElementById('fileInput');
const progress = document.getElementById('progress');
const summary = document.getElementById('summary');
const result = document.getElementById('result');

drop.addEventListener('click', () => input.click());
input.addEventListener('change', () => uploadFile(input.files[0]));

drop.addEventListener('dragover', (e) => { e.preventDefault(); drop.classList.add('hover'); });
drop.addEventListener('dragleave', () => drop.classList.remove('hover'));
drop.addEventListener('drop', (e) => {
  e.preventDefault(); drop.classList.remove('hover');
  const f = e.dataTransfer.files[0];
  uploadFile(f);
});

async function uploadFile(file) {
  if (!file) return;
  if (file.size > 20 * 1024 * 1024) { alert('File too large'); return; }
  if (file.type !== 'application/pdf') { alert('Only PDFs allowed'); return; }

  const fd = new FormData();
  fd.append('statement', file);
  progress.textContent = 'Uploading...';
  summary.hidden = true;

  try {
    const res = await fetch('/upload', { method: 'POST', body: fd });
    const json = await res.json();
    progress.textContent = '';
    summary.hidden = false;
    result.textContent = JSON.stringify(json.summary || json, null, 2);
  } catch (err) {
    progress.textContent = '';
    alert('Upload failed');
  }
}
