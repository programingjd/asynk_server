(function(){
  function checkState() {
    return document.readyState === 'interactive';
  }
  if (checkState()) init();
  else document.onreadystatechange = () => {if (checkState()) init();};
  function init() {
    const header = document.querySelector('header');
    const images = document.querySelectorAll('section>img');
    images.forEach(it => {
      it.classList.add('link');
      it.addEventListener('click', e => {
        const previous = document.querySelector('.selected');
        if (previous) previous.classList.remove('selected');
        it.classList.toggle('selected', true);
        const src = it.src;
        const i = src.lastIndexOf('/');
        const j = src.lastIndexOf('.');
        header.innerText = i === -1 ? src.substring(0, j) : src.substring(i + 1,  j);
      })
    })
  }
})();
