const canvas = document.getElementById("canvas");
const ctx = canvas.getContext("2d");
ctx.canvas.width  = window.innerWidth;
ctx.canvas.height = window.innerHeight;

function a(count) {
    const newCount = count - 1;
    
    console.log(`newCount is ${newCount}`)
    
    const randomNumber = Math.random();
    const randomNumber2 = Math.random();
    const randomNumber3 = Math.random();
    
    ctx.fillStyle = `rgba(
        ${Math.floor(255 * randomNumber)},
        ${Math.floor(255 * randomNumber2)},
        ${Math.floor(255 * randomNumber3)}, 0.25)`;
    ctx.fillRect(
        Math.floor(randomNumber * ctx.canvas.width), 
        Math.floor(randomNumber2 * ctx.canvas.height), 
        (Math.round(Math.random()) * 2 - 1) * Math.floor(randomNumber * ctx.canvas.width) + 300, 
        (Math.round(Math.random()) * 2 - 1) * Math.floor(randomNumber2 * ctx.canvas.height) + 100);
    
    if (newCount > 0) {
        setTimeout(() => a(newCount), 10 * 1000);
    }

}

a(10);