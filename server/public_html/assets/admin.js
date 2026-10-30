'use strict';
document.querySelectorAll('form[data-confirm]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
        if (!window.confirm(form.dataset.confirm)) event.preventDefault();
    });
});
// Progressive UI only: all writes still submit ordinary CSRF/revision-checked forms.
document.querySelectorAll('.watchlist-form').forEach(function (form) {
    const body = form.querySelector('tbody');
    function selected() { return Array.from(body.rows).filter(row => row.querySelector('[data-included]').checked); }
    function controls() {
        const rows = selected();
        Array.from(body.rows).forEach(function (row) {
            const index = rows.indexOf(row);
            row.querySelector('[data-move="-1"]').disabled = index < 1;
            row.querySelector('[data-move="1"]').disabled = index < 0 || index === rows.length - 1;
        });
    }
    function renumber() { selected().forEach((row, index) => { row.querySelector('[data-order]').value = String(index); }); }
    form.addEventListener('click', function (event) {
        const button = event.target.closest('[data-move]');
        if (!button || button.disabled || !form.contains(button)) return;
        const row = button.closest('tr'), rows = selected(), index = rows.indexOf(row);
        const neighbor = rows[index + Number(button.dataset.move)];
        if (!neighbor) return;
        if (button.dataset.move === '-1') body.insertBefore(row, neighbor);
        else body.insertBefore(neighbor, row);
        renumber(); controls();
    });
    form.addEventListener('change', function (event) {
        if (event.target.matches('[data-included]')) { renumber(); controls(); }
    });
    controls();
});
