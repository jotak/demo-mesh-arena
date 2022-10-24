var eb = new EventBus('/eventbus/');
eb.enableReconnect(true);

setTimeout(function() {
  $('#loading').attr("src", "./next.png");
}, 4000);

function displayGameObject(obj) {
  $('#loading').remove();
  var rawElt = document.getElementById(obj.id);
  if (!rawElt) {
    if (obj.playerRef) {
      var innerSpan = '<span style="position:relative;top:4px;left:9px;">'
        + obj.playerRef.name.charAt(0)
        + '</span>';
      $('#right-pane').append('<div id="players-' + obj.id + '" '
        + ' onclick="selectPlayer(\'' + obj.playerRef.name + '\', \'' + obj.playerRef.ip + '\')"'
        + '><div style="' + obj.style + ' position:relative; transform:none;">' + innerSpan
        + '<span style="position:relative;top:4px;left:25px;">' + obj.playerRef.name + '</span>'
        + '</div></div>');
      $('#board').append('<div id="' + obj.id + '" style="' + obj.style + '">' + innerSpan + '</div>');
    } else {
      $('#board').append('<div id="' + obj.id + '" style="' + obj.style + '">' + (obj.text || '') + '</div>');
    }
  } else {
    if (obj.style) {
      rawElt.style.cssText = obj.style;
    }
    if (obj.text) {
      rawElt.innerHTML = obj.text;
    }
    if (obj.x) {
      $('#' + obj.id).css('top', obj.y + 'px')
        .css('left', obj.x + 'px');
    }
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
    $('#players-' + msg.body).remove();
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

function selectPlayer(name, ip) {
  var selected = $('#selected');
  selected.contents().remove();
  selected.append("Player selected: " + name + "&nbsp;"
    + "<input type='text' id='whoareyou' placeholder='Who are you?' />"
    + "<button type='button' onclick='shoot(\"" + ip + "\")'>Shoot</button>");
}

function shoot(ip) {
  var who = document.getElementById("whoareyou").value;
  eb.send("shoot", {ip: ip, who: who});
}
