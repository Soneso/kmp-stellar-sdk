import SwiftUI
import shared

// MARK: - Contract Info View

struct ContractInfoView: View {
    let contractInfo: SorobanContractInfo
    
    var body: some View {
        VStack(spacing: 16) {
            // Success header
            VStack(alignment: .leading, spacing: 8) {
                Text("Contract Found")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Material3Colors.onSuccessContainer)
                
                Text("Successfully fetched and parsed contract details")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSuccessContainer)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.successContainer)
            .cornerRadius(12)
            
            // Contract Metadata Card
            ContractMetadataView(contractInfo: contractInfo)
            
            // Contract Spec Entries Card
            ContractSpecEntriesView(specEntries: contractInfo.specEntries)
        }
    }
}

// MARK: - Contract Metadata View

struct ContractMetadataView: View {
    let contractInfo: SorobanContractInfo
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Contract Metadata")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            Divider()
            
            // Environment Interface Version
            DetailRow(label: "Environment Interface Version", value: String(contractInfo.envInterfaceVersion))
            
            // Meta entries
            if !contractInfo.metaEntries.isEmpty {
                Spacer().frame(height: 8)
                Text("Meta Entries (\(contractInfo.metaEntries.count))")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                
                ForEach(Array(contractInfo.metaEntries.keys.sorted()), id: \.self) { key in
                    if let value = contractInfo.metaEntries[key] {
                        DetailRow(label: key, value: value as! String, monospace: true)
                    }
                }
            } else {
                DetailRow(label: "Meta Entries", value: "None")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.surfaceVariant)
        .cornerRadius(12)
    }
}

// MARK: - Contract Spec Entries View

struct ContractSpecEntriesView: View {
    let specEntries: [SCSpecEntryXdr]
    
    var sortedEntries: [SCSpecEntryXdr] {
        specEntries.sorted { entry1, entry2 in
            priority(for: entry1) < priority(for: entry2)
        }
    }
    
    private func priority(for entry: SCSpecEntryXdr) -> Int {
        if entry is SCSpecEntryXdr.FunctionV0 { return 0 }
        if entry is SCSpecEntryXdr.UdtStructV0 { return 1 }
        if entry is SCSpecEntryXdr.UdtUnionV0 { return 2 }
        if entry is SCSpecEntryXdr.UdtEnumV0 { return 3 }
        if entry is SCSpecEntryXdr.UdtErrorEnumV0 { return 4 }
        if entry is SCSpecEntryXdr.EventV0 { return 5 }
        return 6
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Contract Spec Entries (\(specEntries.count))")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            Divider()
            
            if sortedEntries.isEmpty {
                Text("No spec entries found")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    .padding(.vertical, 8)
            } else {
                ForEach(Array(sortedEntries.enumerated()), id: \.offset) { index, entry in
                    if index > 0 {
                        Divider()
                            .padding(.vertical, 8)
                    }
                    SpecEntryItemView(entry: entry)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.surfaceVariant)
        .cornerRadius(12)
    }
}

// MARK: - Spec Entry Item View

struct SpecEntryItemView: View {
    let entry: SCSpecEntryXdr
    @State private var isExpanded = false
    
    var body: some View {
        if let function = entry as? SCSpecEntryXdr.FunctionV0 {
            FunctionSpecView(function: function.value, isExpanded: $isExpanded)
        } else if let structEntry = entry as? SCSpecEntryXdr.UdtStructV0 {
            StructSpecView(structDef: structEntry.value, isExpanded: $isExpanded)
        } else if let union = entry as? SCSpecEntryXdr.UdtUnionV0 {
            UnionSpecView(unionDef: union.value, isExpanded: $isExpanded)
        } else if let enumEntry = entry as? SCSpecEntryXdr.UdtEnumV0 {
            EnumSpecView(enumDef: enumEntry.value, isExpanded: $isExpanded)
        } else if let errorEnum = entry as? SCSpecEntryXdr.UdtErrorEnumV0 {
            ErrorEnumSpecView(errorEnum: errorEnum.value, isExpanded: $isExpanded)
        } else if let event = entry as? SCSpecEntryXdr.EventV0 {
            EventSpecView(event: event.value, isExpanded: $isExpanded)
        }
    }
}

// MARK: - Function Spec View

struct FunctionSpecView: View {
    let function: SCSpecFunctionV0Xdr
    @Binding var isExpanded: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(action: { isExpanded.toggle() }) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Function: \(function.name)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    if !function.doc.isEmpty {
                        Text(function.doc)
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                    
                    if isExpanded {
                        functionDetails
                    } else {
                        HStack(spacing: 12) {
                            Text("Inputs: \(function.inputs.count)")
                                .font(.system(size: 12))
                            Text("Outputs: \(function.outputs.count)")
                                .font(.system(size: 12))
                        }
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    }
                    
                    Text(isExpanded ? "Click to collapse" : "Click to expand")
                        .font(.system(size: 11))
                        .foregroundStyle(Material3Colors.primary)
                        .padding(.top, 4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Material3Colors.primaryContainer.opacity(0.3))
        .cornerRadius(8)
    }
    
    @ViewBuilder
    private var functionDetails: some View {
        if !function.inputs.isEmpty {
            Spacer().frame(height: 4)
            Text("Inputs (\(function.inputs.count)):")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            ForEach(Array(function.inputs.enumerated()), id: \.offset) { index, input in
                VStack(alignment: .leading, spacing: 2) {
                    Text("[\(index)] \(input.name)")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    Text("Type: \(getSpecTypeInfo(input.type))")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                    if !input.doc.isEmpty {
                        Text("Doc: \(input.doc)")
                            .font(.system(size: 11))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                }
                .padding(.leading, 12)
            }
        } else {
            Text("Inputs: None")
                .font(.system(size: 12))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
        
        if !function.outputs.isEmpty {
            Spacer().frame(height: 4)
            Text("Outputs (\(function.outputs.count)):")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            ForEach(Array(function.outputs.enumerated()), id: \.offset) { index, output in
                Text("[\(index)] \(getSpecTypeInfo(output))")
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                    .padding(.leading, 12)
            }
        } else {
            Spacer().frame(height: 4)
            Text("Outputs: None (void)")
                .font(.system(size: 12))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
    }
}

// MARK: - Struct Spec View

struct StructSpecView: View {
    let structDef: SCSpecUDTStructV0Xdr
    @Binding var isExpanded: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(action: { isExpanded.toggle() }) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Struct: \(structDef.name)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    if !structDef.doc.isEmpty {
                        Text(structDef.doc)
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                    
                    if !structDef.lib.isEmpty {
                        Text("Lib: \(structDef.lib)")
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                    }
                    
                    if isExpanded {
                        structDetails
                    } else {
                        Text("Fields: \(structDef.fields.count)")
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                    }
                    
                    Text(isExpanded ? "Click to collapse" : "Click to expand")
                        .font(.system(size: 11))
                        .foregroundStyle(Material3Colors.primary)
                        .padding(.top, 4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Material3Colors.secondaryContainer.opacity(0.3))
        .cornerRadius(8)
    }
    
    @ViewBuilder
    private var structDetails: some View {
        if !structDef.fields.isEmpty {
            Spacer().frame(height: 4)
            Text("Fields (\(structDef.fields.count)):")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            ForEach(Array(structDef.fields.enumerated()), id: \.offset) { index, field in
                VStack(alignment: .leading, spacing: 2) {
                    Text("[\(index)] \(field.name)")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    Text("Type: \(getSpecTypeInfo(field.type))")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                    if !field.doc.isEmpty {
                        Text("Doc: \(field.doc)")
                            .font(.system(size: 11))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                }
                .padding(.leading, 12)
            }
        } else {
            Text("Fields: None")
                .font(.system(size: 12))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
    }
}

// MARK: - Union Spec View

struct UnionSpecView: View {
    let unionDef: SCSpecUDTUnionV0Xdr
    @Binding var isExpanded: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(action: { isExpanded.toggle() }) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Union: \(unionDef.name)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    if !unionDef.doc.isEmpty {
                        Text(unionDef.doc)
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                    
                    if !unionDef.lib.isEmpty {
                        Text("Lib: \(unionDef.lib)")
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                    }
                    
                    if isExpanded {
                        // Cases detail
                        if !unionDef.cases.isEmpty {
                            Spacer().frame(height: 4)
                            Text("Cases (\(unionDef.cases.count)):")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(Material3Colors.onSurfaceVariant)
                            
                            ForEach(Array(unionDef.cases.enumerated()), id: \.offset) { index, uCase in
                                VStack(alignment: .leading, spacing: 2) {
                                    if let voidCase = uCase as? SCSpecUDTUnionCaseV0Xdr.VoidCase {
                                        Text("[\(index)] \(voidCase.value.name) (void)")
                                            .font(.system(size: 12, weight: .medium))
                                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                                        if !voidCase.value.doc.isEmpty {
                                            Text("Doc: \(voidCase.value.doc)")
                                                .font(.system(size: 11))
                                                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                                        }
                                    } else if let tupleCase = uCase as? SCSpecUDTUnionCaseV0Xdr.TupleCase {
                                        Text("[\(index)] \(tupleCase.value.name) (tuple)")
                                            .font(.system(size: 12, weight: .medium))
                                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                                        let types = tupleCase.value.type.map { getSpecTypeInfo($0) }.joined(separator: ", ")
                                        Text("Types: [\(types)]")
                                            .font(.system(size: 11, design: .monospaced))
                                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                                        if !tupleCase.value.doc.isEmpty {
                                            Text("Doc: \(tupleCase.value.doc)")
                                                .font(.system(size: 11))
                                                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                                        }
                                    }
                                }
                                .padding(.leading, 12)
                            }
                        } else {
                            Text("Cases: None")
                                .font(.system(size: 12))
                                .foregroundStyle(Material3Colors.onSurfaceVariant)
                        }
                    } else {
                        // Summary when collapsed
                        Text("Cases: \(unionDef.cases.count)")
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                    }
                    
                    Text(isExpanded ? "Click to collapse" : "Click to expand")
                        .font(.system(size: 11))
                        .foregroundStyle(Material3Colors.primary)
                        .padding(.top, 4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Material3Colors.tertiaryContainer.opacity(0.3))
        .cornerRadius(8)
    }
}


// MARK: - Enum Spec View

struct EnumSpecView: View {
    let enumDef: SCSpecUDTEnumV0Xdr
    @Binding var isExpanded: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(action: { isExpanded.toggle() }) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Enum: \(enumDef.name)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    if !enumDef.doc.isEmpty {
                        Text(enumDef.doc)
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                    
                    Text("Cases: \(enumDef.cases.count)")
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    Text(isExpanded ? "Click to collapse" : "Click to expand")
                        .font(.system(size: 11))
                        .foregroundStyle(Material3Colors.primary)
                        .padding(.top, 4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Material3Colors.surfaceVariant.opacity(0.5))
        .cornerRadius(8)
    }
}

// MARK: - Error Enum Spec View

struct ErrorEnumSpecView: View {
    let errorEnum: SCSpecUDTErrorEnumV0Xdr
    @Binding var isExpanded: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(action: { isExpanded.toggle() }) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Error Enum: \(errorEnum.name)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    if !errorEnum.doc.isEmpty {
                        Text(errorEnum.doc)
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                    
                    Text("Cases: \(errorEnum.cases.count)")
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    Text(isExpanded ? "Click to collapse" : "Click to expand")
                        .font(.system(size: 11))
                        .foregroundStyle(Material3Colors.primary)
                        .padding(.top, 4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Material3Colors.errorContainer.opacity(0.3))
        .cornerRadius(8)
    }
}

// MARK: - Event Spec View

struct EventSpecView: View {
    let event: SCSpecEventV0Xdr
    @Binding var isExpanded: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(action: { isExpanded.toggle() }) {
                VStack(alignment: .leading, spacing: 6) {
                    headerView
                    if isExpanded {
                        expandedView
                    } else {
                        collapsedView
                    }
                    toggleHintText
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Material3Colors.primaryContainer.opacity(0.5))
        .cornerRadius(8)
    }
    
    @ViewBuilder
    private var headerView: some View {
        Text("Event: \(event.name)")
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(Material3Colors.onSurfaceVariant)
        
        if !event.doc.isEmpty {
            Text(event.doc)
                .font(.system(size: 12))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
        }
        
        Text("Lib: \(event.lib)")
            .font(.system(size: 12, design: .monospaced))
            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
    }
    
    @ViewBuilder
    private var expandedView: some View {
        // Prefix Topics
        if !event.prefixTopics.isEmpty {
            Spacer().frame(height: 4)
            Text("Prefix Topics (\(event.prefixTopics.count)):")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            ForEach(Array(event.prefixTopics.enumerated()), id: \.offset) { index, topic in
                Text("[\(index)] \(topic)")
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                    .padding(.leading, 12)
            }
        } else {
            Text("Prefix Topics: None")
                .font(.system(size: 12))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
        
        // Parameters
        if !event.params.isEmpty {
            Spacer().frame(height: 4)
            Text("Params (\(event.params.count)):")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            ForEach(Array(event.params.enumerated()), id: \.offset) { index, param in
                parameterView(index: index, param: param)
            }
        } else {
            Text("Params: None")
                .font(.system(size: 12))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
        
        // Data format
        Spacer().frame(height: 4)
        Text("Data Format: \(getDataFormatString())")
            .font(.system(size: 12))
            .foregroundStyle(Material3Colors.onSurfaceVariant)
    }
    
    @ViewBuilder
    private func parameterView(index: Int, param: SCSpecEventParamV0Xdr) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("[\(index)] \(param.name)")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            Text("Type: \(getSpecTypeInfo(param.type))")
                .font(.system(size: 11, design: .monospaced))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
            Text("Location: \(getLocationString(param.location))")
                .font(.system(size: 11))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
            
            if !param.doc.isEmpty {
                Text("Doc: \(param.doc)")
                    .font(.system(size: 11))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
            }
        }
        .padding(.leading, 12)
    }
    
    @ViewBuilder
    private var collapsedView: some View {
        HStack(spacing: 12) {
            Text("Prefix Topics: \(event.prefixTopics.count)")
                .font(.system(size: 12))
            Text("Params: \(event.params.count)")
                .font(.system(size: 12))
        }
        .foregroundStyle(Material3Colors.onSurfaceVariant)
    }
    
    private var toggleHintText: some View {
        Text(isExpanded ? "Click to collapse" : "Click to expand")
            .font(.system(size: 11))
            .foregroundStyle(Material3Colors.primary)
            .padding(.top, 4)
    }
    
    private func getLocationString(_ location: SCSpecEventParamLocationV0Xdr) -> String {
        switch location {
        case .scSpecEventParamLocationData:
            return "data"
        case .scSpecEventParamLocationTopicList:
            return "topic list"
        default:
            return "unknown"
        }
    }
    
    private func getDataFormatString() -> String {
        switch event.dataFormat {
        case .scSpecEventDataFormatSingleValue:
            return "single value"
        case .scSpecEventDataFormatMap:
            return "map"
        case .scSpecEventDataFormatVec:
            return "vec"
        default:
            return "unknown"
        }
    }
}




// MARK: - Contract Error View

struct ContractErrorView: View {
    let error: ContractDetailsResult.Error
    
    var body: some View {
        VStack(spacing: 16) {
            // Error card
            VStack(alignment: .leading, spacing: 8) {
                Text("Error")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Material3Colors.onErrorContainer)
                
                Text(error.message)
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onErrorContainer)
                
                if let exception = error.exception {
                    Text("Technical details: \(exception.message ?? "Unknown error")")
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.errorContainer)
            .cornerRadius(12)
            
            // Troubleshooting card
            VStack(alignment: .leading, spacing: 8) {
                Text("Troubleshooting")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
                
                VStack(alignment: .leading, spacing: 4) {
                    Text("• Verify the contract ID is valid (starts with 'C' and is 56 characters)")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                    
                    Text("• Make sure the contract exists on testnet and has been deployed")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                    
                    Text("• Check your internet connection")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                    
                    Text("• Try again in a moment if you're being rate-limited")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                }
                .padding(.leading, 8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.secondaryContainer)
            .cornerRadius(12)
        }
    }
}

// MARK: - Helper Functions

private func getSpecTypeInfo(_ specType: SCSpecTypeDefXdr) -> String {
    switch specType.discriminant {
    case .scSpecTypeVal:
        return "val"
    case .scSpecTypeBool:
        return "bool"
    case .scSpecTypeVoid:
        return "void"
    case .scSpecTypeError:
        return "error"
    case .scSpecTypeU32:
        return "u32"
    case .scSpecTypeI32:
        return "i32"
    case .scSpecTypeU64:
        return "u64"
    case .scSpecTypeI64:
        return "i64"
    case .scSpecTypeTimepoint:
        return "timepoint"
    case .scSpecTypeDuration:
        return "duration"
    case .scSpecTypeU128:
        return "u128"
    case .scSpecTypeI128:
        return "i128"
    case .scSpecTypeU256:
        return "u256"
    case .scSpecTypeI256:
        return "i256"
    case .scSpecTypeBytes:
        return "bytes"
    case .scSpecTypeString:
        return "string"
    case .scSpecTypeSymbol:
        return "symbol"
    case .scSpecTypeAddress:
        return "address"
    case .scSpecTypeMuxedAddress:
        return "muxed address"
    case .scSpecTypeOption:
        if let option = specType as? SCSpecTypeDefXdr.Option {
            let valueType = getSpecTypeInfo(option.value.valueType)
            return "option (value type: \(valueType))"
        }
        return "option"
    case .scSpecTypeResult:
        if let result = specType as? SCSpecTypeDefXdr.Result {
            let okType = getSpecTypeInfo(result.value.okType)
            let errorType = getSpecTypeInfo(result.value.errorType)
            return "result (ok type: \(okType), error type: \(errorType))"
        }
        return "result"
    case .scSpecTypeVec:
        if let vec = specType as? SCSpecTypeDefXdr.Vec {
            let elementType = getSpecTypeInfo(vec.value.elementType)
            return "vec (element type: \(elementType))"
        }
        return "vec"
    case .scSpecTypeMap:
        if let map = specType as? SCSpecTypeDefXdr.Map {
            let keyType = getSpecTypeInfo(map.value.keyType)
            let valueType = getSpecTypeInfo(map.value.valueType)
            return "map (key type: \(keyType), value type: \(valueType))"
        }
        return "map"
    case .scSpecTypeTuple:
        if let tuple = specType as? SCSpecTypeDefXdr.Tuple {
            let valueTypesStr = tuple.value.valueTypes.map { getSpecTypeInfo($0) }.joined(separator: ", ")
            return "tuple (value types: [\(valueTypesStr)])"
        }
        return "tuple"
    case .scSpecTypeBytesN:
        if let bytesN = specType as? SCSpecTypeDefXdr.BytesN {
            return "bytesN (n: \(bytesN.value.n))"
        }
        return "bytesN"
    case .scSpecTypeUdt:
        if let udt = specType as? SCSpecTypeDefXdr.Udt {
            return "udt (name: \(udt.value.name))"
        }
        return "udt"
    default:
        return "unknown"
    }
}
