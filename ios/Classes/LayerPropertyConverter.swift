// This file is generated by
// ./scripts/lib/generate.dart

import Mapbox
import MapboxAnnotationExtension

class LayerPropertyConverter {
    class func addSymbolProperties(symbolLayer: MGLSymbolStyleLayer, properties: [String: String]) {
        for (propertyName, propertyValue) in properties {
            let expression = interpretExpression(propertyName: propertyName, expression: propertyValue)
            switch propertyName {
                case "icon-opacity":
                    symbolLayer.iconOpacity = expression;
                break;
                case "icon-color":
                    symbolLayer.iconColor = expression;
                break;
                case "icon-halo-color":
                    symbolLayer.iconHaloColor = expression;
                break;
                case "icon-halo-width":
                    symbolLayer.iconHaloWidth = expression;
                break;
                case "icon-halo-blur":
                    symbolLayer.iconHaloBlur = expression;
                break;
                case "icon-translate":
                    symbolLayer.iconTranslation = expression;
                break;
                case "icon-translate-anchor":
                    symbolLayer.iconTranslationAnchor = expression;
                break;
                case "text-opacity":
                    symbolLayer.textOpacity = expression;
                break;
                case "text-color":
                    symbolLayer.textColor = expression;
                break;
                case "text-halo-color":
                    symbolLayer.textHaloColor = expression;
                break;
                case "text-halo-width":
                    symbolLayer.textHaloWidth = expression;
                break;
                case "text-halo-blur":
                    symbolLayer.textHaloBlur = expression;
                break;
                case "text-translate":
                    symbolLayer.textTranslation = expression;
                break;
                case "text-translate-anchor":
                    symbolLayer.textTranslationAnchor = expression;
                break;
                case "symbol-placement":
                    symbolLayer.symbolPlacement = expression;
                break;
                case "symbol-spacing":
                    symbolLayer.symbolSpacing = expression;
                break;
                case "symbol-avoid-edges":
                    symbolLayer.symbolAvoidsEdges = expression;
                break;
                case "symbol-sort-key":
                    symbolLayer.symbolSortKey = expression;
                break;
                case "symbol-z-order":
                    symbolLayer.symbolZOrder = expression;
                break;
                case "icon-allow-overlap":
                    symbolLayer.iconAllowsOverlap = expression;
                break;
                case "icon-ignore-placement":
                    symbolLayer.iconIgnoresPlacement = expression;
                break;
                case "icon-optional":
                    symbolLayer.iconOptional = expression;
                break;
                case "icon-rotation-alignment":
                    symbolLayer.iconRotationAlignment = expression;
                break;
                case "icon-size":
                    symbolLayer.iconScale = expression;
                break;
                case "icon-text-fit":
                    symbolLayer.iconTextFit = expression;
                break;
                case "icon-text-fit-padding":
                    symbolLayer.iconTextFitPadding = expression;
                break;
                case "icon-image":
                    symbolLayer.iconImageName = expression;
                break;
                case "icon-rotate":
                    symbolLayer.iconRotation = expression;
                break;
                case "icon-padding":
                    symbolLayer.iconPadding = expression;
                break;
                case "icon-keep-upright":
                    symbolLayer.keepsIconUpright = expression;
                break;
                case "icon-offset":
                    symbolLayer.iconOffset = expression;
                break;
                case "icon-anchor":
                    symbolLayer.iconAnchor = expression;
                break;
                case "icon-pitch-alignment":
                    symbolLayer.iconPitchAlignment = expression;
                break;
                case "text-pitch-alignment":
                    symbolLayer.textPitchAlignment = expression;
                break;
                case "text-rotation-alignment":
                    symbolLayer.textRotationAlignment = expression;
                break;
                case "text-field":
                    symbolLayer.text = expression;
                break;
                case "text-font":
                    symbolLayer.textFontNames = expression;
                break;
                case "text-size":
                    symbolLayer.textFontSize = expression;
                break;
                case "text-max-width":
                    symbolLayer.maximumTextWidth = expression;
                break;
                case "text-line-height":
                    symbolLayer.textLineHeight = expression;
                break;
                case "text-letter-spacing":
                    symbolLayer.textLetterSpacing = expression;
                break;
                case "text-justify":
                    symbolLayer.textJustification = expression;
                break;
                case "text-radial-offset":
                    symbolLayer.textRadialOffset = expression;
                break;
                case "text-variable-anchor":
                    symbolLayer.textVariableAnchor = expression;
                break;
                case "text-anchor":
                    symbolLayer.textAnchor = expression;
                break;
                case "text-max-angle":
                    symbolLayer.maximumTextAngle = expression;
                break;
                case "text-writing-mode":
                    symbolLayer.textWritingModes = expression;
                break;
                case "text-rotate":
                    symbolLayer.textRotation = expression;
                break;
                case "text-padding":
                    symbolLayer.textPadding = expression;
                break;
                case "text-keep-upright":
                    symbolLayer.keepsTextUpright = expression;
                break;
                case "text-transform":
                    symbolLayer.textTransform = expression;
                break;
                case "text-offset":
                    symbolLayer.textOffset = expression;
                break;
                case "text-allow-overlap":
                    symbolLayer.textAllowsOverlap = expression;
                break;
                case "text-ignore-placement":
                    symbolLayer.textIgnoresPlacement = expression;
                break;
                case "text-optional":
                    symbolLayer.textOptional = expression;
                break;
                case "visibility":
                    symbolLayer.isVisible = propertyValue == "visible";
                break;
             
                default:
                    break
            }
        }
    }

    class func addCircleProperties(circleLayer: MGLCircleStyleLayer, properties: [String: String]) {
        for (propertyName, propertyValue) in properties {
            let expression = interpretExpression(propertyName: propertyName, expression: propertyValue)
            switch propertyName {
                case "circle-radius":
                    circleLayer.circleRadius = expression;
                break;
                case "circle-color":
                    circleLayer.circleColor = expression;
                break;
                case "circle-blur":
                    circleLayer.circleBlur = expression;
                break;
                case "circle-opacity":
                    circleLayer.circleOpacity = expression;
                break;
                case "circle-translate":
                    circleLayer.circleTranslation = expression;
                break;
                case "circle-translate-anchor":
                    circleLayer.circleTranslationAnchor = expression;
                break;
                case "circle-pitch-scale":
                    circleLayer.circleScaleAlignment = expression;
                break;
                case "circle-pitch-alignment":
                    circleLayer.circlePitchAlignment = expression;
                break;
                case "circle-stroke-width":
                    circleLayer.circleStrokeWidth = expression;
                break;
                case "circle-stroke-color":
                    circleLayer.circleStrokeColor = expression;
                break;
                case "circle-stroke-opacity":
                    circleLayer.circleStrokeOpacity = expression;
                break;
                case "circle-sort-key":
                    circleLayer.circleSortKey = expression;
                break;
                case "visibility":
                    circleLayer.isVisible = propertyValue == "visible";
                break;
             
                default:
                    break
            }
        }
    }

    class func addLineProperties(lineLayer: MGLLineStyleLayer, properties: [String: String]) {
        for (propertyName, propertyValue) in properties {
            let expression = interpretExpression(propertyName: propertyName, expression: propertyValue)
            switch propertyName {
                case "line-opacity":
                    lineLayer.lineOpacity = expression;
                break;
                case "line-color":
                    lineLayer.lineColor = expression;
                break;
                case "line-translate":
                    lineLayer.lineTranslation = expression;
                break;
                case "line-translate-anchor":
                    lineLayer.lineTranslationAnchor = expression;
                break;
                case "line-width":
                    lineLayer.lineWidth = expression;
                break;
                case "line-gap-width":
                    lineLayer.lineGapWidth = expression;
                break;
                case "line-offset":
                    lineLayer.lineOffset = expression;
                break;
                case "line-blur":
                    lineLayer.lineBlur = expression;
                break;
                case "line-dasharray":
                    lineLayer.lineDashPattern = expression;
                break;
                case "line-pattern":
                    lineLayer.linePattern = expression;
                break;
                case "line-gradient":
                    lineLayer.lineGradient = expression;
                break;
                case "line-cap":
                    lineLayer.lineCap = expression;
                break;
                case "line-join":
                    lineLayer.lineJoin = expression;
                break;
                case "line-miter-limit":
                    lineLayer.lineMiterLimit = expression;
                break;
                case "line-round-limit":
                    lineLayer.lineRoundLimit = expression;
                break;
                case "line-sort-key":
                    lineLayer.lineSortKey = expression;
                break;
                case "visibility":
                    lineLayer.isVisible = propertyValue == "visible";
                break;
             
                default:
                    break
            }
        }
    }

    class func addFillProperties(fillLayer: MGLFillStyleLayer, properties: [String: String]) {
        for (propertyName, propertyValue) in properties {
            let expression = interpretExpression(propertyName: propertyName, expression: propertyValue)
            switch propertyName {
                case "fill-antialias":
                    fillLayer.fillAntialiased = expression;
                break;
                case "fill-opacity":
                    fillLayer.fillOpacity = expression;
                break;
                case "fill-color":
                    fillLayer.fillColor = expression;
                break;
                case "fill-outline-color":
                    fillLayer.fillOutlineColor = expression;
                break;
                case "fill-translate":
                    fillLayer.fillTranslation = expression;
                break;
                case "fill-translate-anchor":
                    fillLayer.fillTranslationAnchor = expression;
                break;
                case "fill-pattern":
                    fillLayer.fillPattern = expression;
                break;
                case "fill-sort-key":
                    fillLayer.fillSortKey = expression;
                break;
                case "visibility":
                    fillLayer.isVisible = propertyValue == "visible";
                break;
             
                default:
                    break
            }
        }
    }

    private class func interpretExpression(propertyName: String, expression: String) -> NSExpression? {
        let isColor = propertyName.contains("color");

        do {
            let json = try JSONSerialization.jsonObject(with: expression.data(using: .utf8)!, options: .fragmentsAllowed)
            // this is required because NSExpression.init(mglJSONObject: json) fails to create
            // a proper Expression if the data of is a hexString
            if isColor {
                if let color = json as? String {
                    return NSExpression(forConstantValue: UIColor(hexString: color))
                }
            }
            // this is required because NSExpression.init(mglJSONObject: json) fails to create
            // a proper Expression if the data of a literal is an array
            if let offset = json as? [Any]{
                if offset.count == 2 && offset.first is String && offset.first as? String == "literal" {
                    if let vector = offset.last as? [Any]{
                        if(vector.count == 2) {
                            if let x = vector.first as? Double, let y = vector.last as? Double {
                                return NSExpression(forConstantValue: NSValue(cgVector: CGVector(dx: x, dy: y)))
                            }
                    
                        }
                    }
                }
            }
            return NSExpression.init(mglJSONObject: json)
        } catch {
        }
        return nil
    }
}
