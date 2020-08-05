var eb = new EventBus('/eventbus/');
eb.enableReconnect(true);

function displayGameObject(obj) {
  var rawElt = document.getElementById(obj.id);
  if (!rawElt) {
    $('#board').append('<div id="' + obj.id + '" style="' + obj.style + '">' + obj.text + '</div>');
  } else {
    if (obj.style) {
      rawElt.style.cssText = obj.style;
    }
    if (obj.text) {
      rawElt.innerHTML = obj.text;
    }
  }
  if (obj.x) {
    $('#' + obj.id).css('top', obj.y + 'px')
      .css('left', obj.x + 'px');
  }
}

eb.onopen = function () {
  console.log('onopen')
  eb.registerHandler('displayGameObject', function (err, msg) {
    if (err) {
        console.log(err);
    }
    displayGameObject(msg.body);
  });
  eb.registerHandler('removeGameObject', function (err, msg) {
    if (err) {
        console.log(err);
    }
    $('#' + msg.body).remove();
  });

  eb.send("init-session", "", function (err, msg) {
    msg.body.forEach(function(obj) {
      displayGameObject(obj);
    });
  });
};

eb.onreconnect = function() {
  console.log('onreconnect')
  $('#board').contents().remove();
};

function centerBall() {
  eb.send("centerBall", "");
}

function randomBall() {
  eb.send("randomBall", "");
}
