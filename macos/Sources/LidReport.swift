import Foundation

enum LidReport {
    static func coarse(_ bytes:[UInt8])->Double? {
        guard bytes.count>=3,bytes[0]==1 else {return nil}
        let value=Int(bytes[1]) | Int(bytes[2])<<8
        return (0...360).contains(value) ? Double(value):nil
    }
    static func fine(_ bytes:[UInt8])->Double? {
        guard bytes.count>=5,bytes[0]==7 else {return nil}
        let value=UInt32(bytes[1]) | UInt32(bytes[2])<<8 | UInt32(bytes[3])<<16 | UInt32(bytes[4])<<24
        return value<=36000 ? Double(value)/100:nil
    }
    static func agrees(fine:Double,coarse:Double)->Bool { abs(fine-coarse)<=1.5 }
}
