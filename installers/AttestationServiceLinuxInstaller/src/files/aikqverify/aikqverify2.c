/* Tpm2 Verify a quote issued by an AIK
 * This is modified for tpm2 based on aikqverify.c fo tpm 1.2 */
/* See aikquote2.c for format of quote data file */

/*
 * Copyright (c) 2009 Hal Finney
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

#include <stdio.h>
#include <string.h>
#include <memory.h>
#include <openssl/pem.h>
#include <openssl/x509.h>
#include <openssl/sha.h>


#ifndef BYTE
#define BYTE unsigned char
#endif

#ifndef UINT8
#define UINT8 unsigned char
#endif

#ifndef UINT16
#define UINT16 unsigned short
#endif

#ifndef UINT32
#define UINT32 unsigned
#endif

typedef struct {
	UINT16 size;
	BYTE *buffer;
} TPM2B_NAME;

typedef struct {
	UINT16 size;
	BYTE *buffer;
} TPM2B_DATA;

typedef struct {
	UINT16 size;
	BYTE *digest;
} TPM2B_DIGEST;

typedef struct {
	UINT16 hashAlg;
	UINT8 size;
	BYTE *pcrSelected;
} TPMS_PCR_SELECTION;

typedef struct {
	UINT16 signAlg;
	UINT16 hashAlg;
	UINT16 size;
	BYTE *signature;
} TPMT_SIGNATURE;

#define SHA1_SIZE 20 // 20 bytes
#define SHA256_SIZE 32 // 32 bytes

int main (int ac, char **av)
{
	FILE		*f_in;
	BYTE		*chal;
	UINT32		chalLen;
	BYTE		*quote;
	UINT32		index =0;
	UINT32		pos =0;
	UINT32		quoteLen;
	RSA		*aikRsa;
	UINT32		selectLen;
	BYTE		*select;
	UINT32		pcrLen;
	BYTE		*pcrs;
	BYTE		*quoted = NULL;
	BYTE		*quotedInfo = NULL;
	UINT16		quotedInfoLen;
	BYTE		*tpmtSig = NULL;
	UINT16		sigAlg;
	UINT16		hashAlg;
	BYTE		*sig;
	UINT32		sigLen;
	BYTE		*recvNonce = NULL;
	UINT32		recvNonceLen;
	UINT32		verifiedLen;
	BYTE		chalmd[20];
	BYTE		md[32]; // SHA256 hash
	BYTE		qinfo[8+20+20];
	TPM2B_NAME	tpm2b_name;
	TPM2B_DATA	tpm2b_data;
	UINT32		pcrBankCount;
	TPMS_PCR_SELECTION	pcr_selection;
	TPM2B_DIGEST	tpm2b_digest;
	TPMT_SIGNATURE	tpmt_signature;
	BYTE 		pcrConcat[SHA256_SIZE * 24];
	BYTE		pcrsDigest[SHA256_SIZE];
	char		*chalfile = NULL;
	int			pcr;
	int			pcri = 0;
	int			ind = 0;
	int			i;

	if (ac == 5 && 0 == strcmp(av[1], "-c")) {
		chalfile = av[2];
		for (i=3; i<ac; i++)
			av[i-2] = av[i];
		ac -= 2;
	}

	if (ac != 3) {
		fprintf (stderr, "Usage: %s [-c challengefile] aikrsafile quotefile\n", av[0]);
		exit (1);
	}

	/* Read challenge file */

	if (chalfile) {
		if ((f_in = fopen (chalfile, "rb")) == NULL) {
			fprintf (stderr, "Unable to open file %s\n", chalfile);
			exit (1);
		}
		fseek (f_in, 0, SEEK_END);
		chalLen = ftell (f_in);
		fseek (f_in, 0, SEEK_SET);
		chal = malloc (chalLen);
		if (fread (chal, 1, chalLen, f_in) != chalLen) {
			fprintf (stderr, "Unable to read file %s\n", chalfile);
			exit (1);
		}
		fclose (f_in);
		SHA1 (chal, chalLen, chalmd);
		//free (chal);
	} else {
		memset (chalmd, 0, sizeof(chalmd));
	}


	/* Read AIK from OpenSSL file */

	if ((f_in = fopen (av[1], "rb")) == NULL) {
		fprintf (stderr, "Unable to open file %s\n", av[1]);
		exit (1);
	}
	if ((aikRsa = PEM_read_RSA_PUBKEY(f_in, NULL, NULL, NULL)) == NULL) {
		fprintf (stderr, "Unable to read RSA file %s\n", av[1]);
		exit (1);
	}
	fclose (f_in);

	/* Read quote file */

	if ((f_in = fopen (av[2], "rb")) == NULL) {
		fprintf (stderr, "Unable to open file %s\n", av[2]);
		exit (1);
	}
	fseek (f_in, 0, SEEK_END);
	quoteLen = ftell (f_in);
	fseek (f_in, 0, SEEK_SET);
	quote = malloc (quoteLen);
	if (fread (quote, 1, quoteLen, f_in) != quoteLen) {
		fprintf (stderr, "Unable to read file %s\n", av[2]);
		exit (1);
	}
	fclose (f_in);

	/* Parse quote file
         * The quote result is constructed as follows for now
	 *
	 * part1: pcr values (0-23), sha1 pcr bank. so the length is 20*24=480
	 * part2: the quoted information: TPM2B_ATTEST
	 * part3: the signature: TPMT_SIGNATURE
	 */

	index = 0;

	/* PART1: quote PCR value */
	// pcr values for pcr 0-23 in bank of SHA1
	if (quoteLen < 2)
		goto badquote;
	select = quote;
	pcrs = quote;
	pcrLen = 480;

	// PART2: quoted infomration structure - TPM2B_ATTEST
	index += pcrLen;	
	quoted = quote + index; 
	quotedInfoLen = (*(UINT16*)quoted); // This is NOT in network order
 	quotedInfo = quoted + 2; // following is the TPMS_ATTEST structure as defined in tpm2.0 spec
        //printf("quoteInfoLen: %d\n", quotedInfoLen);
	
	//qualifiedSigner -- skip the magic header and type
	index += 2;
	index += 6;

	// tpm2b_name
	tpm2b_name.size = ntohs(*(UINT16*)(quote + index));
	index += 2;
	tpm2b_name.buffer = quote + index;
        //printf("tpm2b_name size: %02x\n", tpm2b_name.size); //This is in Network Order

	//tpm2b_data	
	index += tpm2b_name.size; // skip tpm2b_name
	tpm2b_data.size = ntohs(*(UINT16*)(quote + index));
	recvNonceLen = tpm2b_data.size;
        //printf("Received Nonce Len: %02x\n", recvNonceLen); //This is in Network Order
	index += 2; // skip UINT16
	/* now compare the received nonce with the chal */
	tpm2b_data.buffer = quote + index;
	recvNonce = tpm2b_data.buffer;
	index += tpm2b_data.size; // skip tpm2b_data
	
	// First verificaiton is to check if the received nonce matches the challenges sent	
	if (memcmp(recvNonce, chal, chalLen) != 0) {
		fprintf(stderr, "Error in comparing the received nonce with the challenge");
		free(chal);
		exit(1);
	} else {
		free(chal);
	}
	
	index += 17; // skip over the TPMS_CLOCKINFO structure
	index += 8;  // skip over the firmware info
	/* TPMU_ATTEST with selected PCR banks and PCRs, and their hash
	 * tpms_quote_info tpml_pcr_selection	
	 *	count 			uint32	0x00000001	 4 bytes -indicates the number of tpms_pcr_slection array
	 *	tpms_pcr_selection	hash algorithm	uint16	2 bytes
	 *				size of bit map	uint8	1 byte
	 *				pcrSelect		size of bytes
	 *	tpms_pcr_selection		
	 *	...				
	 *	tpm2b_digest		size	0x0020	2 bytes	
	 *				digest	32 bytes of hash
	 */
	pcrBankCount = ntohl(*(UINT32*)(quote + index));
	//printf("bank count: %02x\n", pcrBankCount);
	index += 4;
	pcr_selection.hashAlg = ntohs(*(UINT16*)(quote + index));
	//printf("pcr bank: %02x\n", pcr_selection.hashAlg);
	index += 2;
	pcr_selection.size = (*(UINT8*)(quote + index));
	//printf("pcr bit size byte: %02x\n", pcr_selection.size);
	index += 1;
	pcr_selection.pcrSelected = quote + index;
	index += pcr_selection.size;

	//NOTE: currently we only limit the selection of one PCR bank for quote
	tpm2b_digest.size = ntohs(*(UINT16*)(quote + index));
	//printf("digest size: %02x\n", tpm2b_digest.size);
	index += 2;
	tpm2b_digest.digest = quote + index;

	/* PART 3: TPMT_SIGNATURE */
	index = 2 + quotedInfoLen; // jump to the TPMT_SIGNATURE strucuture
        tpmtSig = quoted + index;
	pos = 0;
	/* sigAlg -indicates the signature algorithm
         * TPMI_SIG_ALG_SCHEME
	 * for now, it is TPM_ALG_RSASSA with value 0x0014
	 */
        tpmt_signature.signAlg = (*(UINT16*)tpmtSig); // This is NOT in networ order
	/* hashAlg used by the signature algorithm indicated above
         * TPM_ALG_HASH
	 * for TPM_ALG_RSASSA, the default hash algorihtm is TPM_ALG_SHA256 with value 0x000b
         */
	pos += 2;
	tpmt_signature.hashAlg = (*(UINT16*)(tpmtSig + pos)); // This is NOT in network order
        //printf("hashAlg: %02x\n", hashAlg);
	
	pos += 2;
	tpmt_signature.size = (*(UINT16*)(tpmtSig + pos)); // This is NOT in network order
        //printf("sigLen: %02x\n", sigLen);

	pos += 2;
        tpmt_signature.signature = tpmtSig + pos;

	/* verify the length of data */	
	verifiedLen = (tpmt_signature.signature + tpmt_signature.size) - quote;
	if (verifiedLen != quoteLen) {
		fprintf (stderr, "Error in parsing data structure in quote\n");
		exit(1);
	}
	
	// Verify RSA signature
        // hash first
        memset(md, 0, sizeof(md));
	SHA256(quotedInfo, quotedInfoLen, md);
        // signature
	if (1 != RSA_verify(NID_sha256, md, sizeof(md), tpmt_signature.signature, tpmt_signature.size, aikRsa)) {
		fprintf (stderr, "Error, bad RSA signature in quote\n");
		exit (2);
	}

	// validate the PCR concatenated digest
	pcri=0; ind=0;
	for (pcr=0; pcr < 8*pcr_selection.size; pcr++) {
		if (pcr_selection.pcrSelected[pcr/8] & (1 << (pcr%8))) {
			memcpy(pcrConcat+ind*20, pcrs+20*pcri, SHA1_SIZE);
			pcri++;
			ind++;
		}
	}
	if (ind<1) {
		fprintf(stderr, "Error, no PCRs selected for quote\n");
		exit(3);
	}
        memset(pcrsDigest, 0, sizeof(pcrsDigest));
	SHA256(pcrConcat, ind*SHA1_SIZE, pcrsDigest);
	if (memcmp(pcrsDigest, tpm2b_digest.digest, tpm2b_digest.size) != 0) {
		fprintf(stderr, "Error in comparing the concatenated PCR digest with the digest in quote");
		exit(4);
	}
	
	/* Print out PCR values  */
	pcri=0;
	for (pcr=0; pcr < 8*pcr_selection.size; pcr++) {
		if (pcr_selection.pcrSelected[pcr/8] & (1 << (pcr%8))) {
			printf ("%2d ", pcr);
			for (i=0; i<20; i++) {
				printf ("%02x", pcrs[20*pcri+i]);
			}
			printf ("\n");
			pcri++;
		}
	}
         
	fflush (stdout);
	fprintf (stderr, "Success!\n");

	return 0;

badquote:
	fprintf (stderr, "Input AIK quote file incorrect format\n");
	return 1;
}