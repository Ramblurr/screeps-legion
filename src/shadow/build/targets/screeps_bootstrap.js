global.console.warn = console.log;
global.console.error = console.log;
global.IS_SCREEPS = true
global.IS_NODE = false

const patchGlobalAsync = (globalObject) => {
    if(!globalObject.timerStor)
        globalObject.timerStor = [];

    // timer generator, calling function every time `ticks % count === 0`
    function *timerGen(func, count, interval = false){
        var id = globalObject.timerStor.length;
        var ticks = 0;
        yield id; // used to return the id to setTimeout() for registration in globalObject.timerStor
        while(true){
            ticks++;
            if(count === 0 || ticks % count === 0 && ticks !== 0){
                if(!interval)
                    globalObject.timerStor[id] = undefined;
                yield func(); // run the function
            } else {
                yield false; // do not run the function
            }
        }
    }

    globalObject.mungeTime =  function mungeTime(t) {
        // setTimeout(func, 0) => should run this tick
        // setTimeout(func, 1) => should run the next tick
        // ...
        // setTimeout(func, n) => should run n ticks from now
        return t+1;
    }

    // must be run inside the loop for the tick count to proceed
    globalObject.runTimeout = function runTimeout(){
        for(var i=0;i<globalObject.timerStor.length;i++){
            if(!!globalObject.timerStor[i]) // ensure generator exists
                globalObject.timerStor[i].next();
        }
    }
    globalObject.setTimeout = function setTimeout(func, time){
        const newTime = mungeTime(time)
        // console.log("setTimeout", time, newTime)
        var t = timerGen(func, newTime);
        var id = t.next().value;
        globalObject.timerStor[id] = t;
        return id;
    }
    globalObject.setImmediate = function (func) {
        return setTimeout(func, 0)
    }
    globalObject.setInterval = function setTimeout(func, time){
        const newTime = mungeTime(time)
        // console.log("setInterval", time, newTime)
        var t = timerGen(func, newTime, true);
        var id = t.next().value;
        globalObject.timerStor[id] = t;
        return id;
    }

    // removes timeout of "id" from activity. id is returned from setTimeout()
    // the same function can be used for intervals and timeouts
    globalObject.clearTimeout = globalObject.clearInterval= function(id){
        if(!!globalObject.timerStor[id])
            globalObject.timerStor[id] = undefined;
    }
}

if(!global.goog) {
    console.log("====GLOBAL RESET====");
    global.goog = {};
    patchGlobalAsync(global);
    var goog = global.goog;
    goog.global = global;
    goog.global.CLOSURE_NO_DEPS = true;
