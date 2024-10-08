var fs = require("fs");
var path = require("path");

module.exports = function (context) {
  console.log("Adding color to colors.xml...");
  var colorsPath = path.join(
    context.opts.projectRoot,
    "platforms",
    "android",
    "app",
    "src",
    "main",
    "res",
    "values",
    "colors.xml"
  );
  fs.readFile(colorsPath, "utf8", function (err, data) {
    if (err) throw err;

    // Check if the color already exists
    if (data.includes('<color name="fcm_notification_color">#2f3030</color>')) {
      console.log("Color already exists, skipping...");
      return;
    }

    var result = data.replace(
      "</resources>",
      '    <color name="fcm_notification_color">#2f3030</color>\n</resources>'
    );

    fs.writeFile(colorsPath, result, "utf8", function (err) {
      if (err) throw err;
    });
  });
};
