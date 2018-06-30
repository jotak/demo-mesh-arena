var eb = new EventBus('/eventbus/');

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


function onStart() {
  eb.send("on-start", "");
}
